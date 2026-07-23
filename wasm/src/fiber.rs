use super::*;

type Cont = Box<dyn FnOnce(Result<Value, String>) -> Step>;
type Resume = Box<dyn FnOnce(PromiseState) -> Step>;
enum Step {
    Done(Result<Value, String>),
    Wait(Promise, Resume),
}

#[derive(Debug, Clone, PartialEq)]
pub enum EvalFiberState {
    Running,
    Suspended,
    Completed(Value),
    Failed(String),
    Cancelled,
}

pub struct EvalFiber {
    env: Rc<RefCell<HashMap<String, Value>>>,
    pending: Option<Promise>,
    resume: Option<Resume>,
    state: EvalFiberState,
}
impl EvalFiber {
    pub fn start(source: &str, env: HashMap<String, Value>) -> Result<Self, String> {
        let forms = parse_forms(source)?;
        let env = Rc::new(RefCell::new(env));
        let step = forms_cps(
            Rc::new(forms),
            0,
            Value::Nil,
            env.clone(),
            Box::new(Step::Done),
        );
        let mut fiber = Self {
            env,
            pending: None,
            resume: None,
            state: EvalFiberState::Running,
        };
        fiber.accept(step);
        Ok(fiber)
    }
    pub fn state(&self) -> EvalFiberState {
        self.state.clone()
    }
    pub fn pending(&self) -> Option<Promise> {
        self.pending.clone()
    }
    pub fn environment(&self) -> HashMap<String, Value> {
        self.env.borrow().clone()
    }
    pub fn resume(&mut self, state: PromiseState) -> EvalFiberState {
        if !matches!(self.state, EvalFiberState::Suspended) {
            return self.state();
        }
        let Some(resume) = self.resume.take() else {
            self.state = EvalFiberState::Failed("fiber continuation missing".into());
            return self.state();
        };
        self.pending = None;
        self.state = EvalFiberState::Running;
        let step = resume(state);
        self.accept(step);
        self.state()
    }
    pub fn cancel(&mut self) -> bool {
        if matches!(
            self.state,
            EvalFiberState::Completed(_) | EvalFiberState::Failed(_) | EvalFiberState::Cancelled
        ) {
            return false;
        }
        self.pending = None;
        self.resume = None;
        self.state = EvalFiberState::Cancelled;
        true
    }
    fn accept(&mut self, step: Step) {
        match step {
            Step::Done(Ok(v)) => self.state = EvalFiberState::Completed(v),
            Step::Done(Err(e)) => self.state = EvalFiberState::Failed(e),
            Step::Wait(p, r) => {
                self.pending = Some(p);
                self.resume = Some(r);
                self.state = EvalFiberState::Suspended;
            }
        }
    }
}

fn forms_cps(
    forms: Rc<Vec<Form>>,
    i: usize,
    last: Value,
    env: Rc<RefCell<HashMap<String, Value>>>,
    k: Cont,
) -> Step {
    if i == forms.len() || matches!(last, Value::Recur(_)) {
        return k(Ok(last));
    }
    let next = forms.clone();
    let e = env.clone();
    one(
        forms[i].clone(),
        env,
        Box::new(move |r| match r {
            Ok(v) => forms_cps(next, i + 1, v, e, k),
            Err(x) => k(Err(x)),
        }),
    )
}
fn values_cps(
    forms: Rc<Vec<Form>>,
    i: usize,
    values: Vec<Value>,
    env: Rc<RefCell<HashMap<String, Value>>>,
    k: Box<dyn FnOnce(Result<Vec<Value>, String>) -> Step>,
) -> Step {
    if i == forms.len() {
        return k(Ok(values));
    }
    let next = forms.clone();
    let e = env.clone();
    one(
        forms[i].clone(),
        env,
        Box::new(move |r| match r {
            Ok(v) => {
                let mut values = values;
                values.push(v);
                values_cps(next, i + 1, values, e, k)
            }
            Err(x) => k(Err(x)),
        }),
    )
}
fn one(form: Form, env: Rc<RefCell<HashMap<String, Value>>>, k: Cont) -> Step {
    match form {
        Form::Map(entries) => {
            let flat = Rc::new(entries.into_iter().flat_map(|(a, b)| [a, b]).collect());
            values_cps(
                flat,
                0,
                Vec::new(),
                env,
                Box::new(move |r| {
                    k(r.map(|v| {
                        Value::Map(
                            v.chunks_exact(2)
                                .map(|p| (p[0].clone(), p[1].clone()))
                                .collect(),
                        )
                    }))
                }),
            )
        }
        Form::Set(v) => values_cps(
            Rc::new(v),
            0,
            Vec::new(),
            env,
            Box::new(move |r| k(r.map(|v| Value::Set(unique_values(v).into())))),
        ),
        Form::Vector(v) => values_cps(
            Rc::new(v),
            0,
            Vec::new(),
            env,
            Box::new(move |r| k(r.map(|v| Value::Vector(v.into())))),
        ),
        Form::List(v) if v.is_empty() => k(Ok(Value::Nil)),
        Form::List(v) if v.len() == 2 && matches!(&v[0],Form::Symbol(n)if n=="quote") => {
            k(literal_value(&v[1]))
        }
        Form::List(v) => list(v, env, k),
        simple => sync(simple, env, k),
    }
}
fn sync(form: Form, env: Rc<RefCell<HashMap<String, Value>>>, k: Cont) -> Step {
    let result = {
        let mut borrowed = env.borrow_mut();
        eval(&form, &mut borrowed)
    };
    k(result)
}
fn list(v: Vec<Form>, env: Rc<RefCell<HashMap<String, Value>>>, k: Cont) -> Step {
    let head = match &v[0] {
        Form::Symbol(n) => Some(n.as_str()),
        _ => None,
    };
    match head {
        Some("deref") => {
            if v.len() != 2 {
                return k(Err("deref expects a var".into()));
            }
            one(
                v[1].clone(),
                env,
                Box::new(move |r| match r {
                    Ok(Value::Var(x)) => k(Ok(x.deref_value())),
                    Ok(Value::Promise(p)) => match p.state() {
                        PromiseState::Fulfilled(x) => k(Ok(x)),
                        PromiseState::Rejected(e) => k(Err(e)),
                        PromiseState::Pending => Step::Wait(
                            p,
                            Box::new(move |s| match s {
                                PromiseState::Fulfilled(x) => k(Ok(x)),
                                PromiseState::Rejected(e) => k(Err(e)),
                                PromiseState::Pending => k(Err("fiber resumed pending".into())),
                            }),
                        ),
                    },
                    Ok(_) => k(Err("deref expects a var or promise".into())),
                    Err(e) => k(Err(e)),
                }),
            )
        }
        Some("do") => forms_cps(Rc::new(v[1..].to_vec()), 0, Value::Nil, env, k),
        Some("if") => {
            if v.len() != 3 && v.len() != 4 {
                return k(Err("if expects 2 or 3 arguments".into()));
            }
            let vv = v.clone();
            let e = env.clone();
            one(
                v[1].clone(),
                env,
                Box::new(move |r| match r {
                    Ok(x) if x.truthy() => one(vv[2].clone(), e, k),
                    Ok(_) if vv.len() == 4 => one(vv[3].clone(), e, k),
                    Ok(_) => k(Ok(Value::Nil)),
                    Err(x) => k(Err(x)),
                }),
            )
        }
        Some("let") => scoped(v, env, k, false),
        Some("loop") => scoped(v, env, k, true),
        Some("recur") => values_cps(
            Rc::new(v[1..].to_vec()),
            0,
            Vec::new(),
            env,
            Box::new(move |r| k(r.map(Value::Recur))),
        ),
        Some("try") => try_cps(v, env, k),
        Some("throw") => {
            if v.len() != 2 {
                return k(Err("throw expects one value".into()));
            }
            one(
                v[1].clone(),
                env,
                Box::new(move |r| match r {
                    Ok(x) => k(Err(format!("thrown: {}", x.display()))),
                    Err(x) => k(Err(x)),
                }),
            )
        }
        Some("def") | Some("set!") | Some("var/set") => bind_form(v, env, k),
        Some("fn") | Some("defn") | Some("var") | Some("ns") => sync(Form::List(v), env, k),
        _ => application(v, env, k),
    }
}

type Previous = Vec<(String, Option<Value>)>;
fn bindings(forms: &[Form], op: &str) -> Result<Vec<Form>, String> {
    let v = match forms.get(1) {
        Some(Form::List(v)) | Some(Form::Vector(v)) => v.clone(),
        _ => return Err(format!("{op} expects bindings")),
    };
    if v.len() % 2 != 0 {
        return Err(format!("{op} bindings require pairs"));
    }
    Ok(v)
}
fn bind_values(
    v: Rc<Vec<Form>>,
    i: usize,
    old: Previous,
    env: Rc<RefCell<HashMap<String, Value>>>,
    k: Box<dyn FnOnce(Result<Previous, String>, Rc<RefCell<HashMap<String, Value>>>) -> Step>,
) -> Step {
    if i == v.len() {
        return k(Ok(old), env);
    }
    let name = match &v[i] {
        Form::Symbol(n) => n.clone(),
        _ => return k(Err("binding name must be a symbol".into()), env),
    };
    let vv = v.clone();
    let e = env.clone();
    one(
        v[i + 1].clone(),
        env,
        Box::new(move |r| match r {
            Ok(x) => {
                let mut old = old;
                let prior = { e.borrow_mut().insert(name.clone(), x) };
                old.push((name, prior));
                bind_values(vv, i + 2, old, e, k)
            }
            Err(x) => k(Err(x), e),
        }),
    )
}
fn restore(env: &mut HashMap<String, Value>, old: Previous) {
    for (n, v) in old.into_iter().rev() {
        if let Some(v) = v {
            env.insert(n, v);
        } else {
            env.remove(&n);
        }
    }
}
fn scoped(v: Vec<Form>, env: Rc<RefCell<HashMap<String, Value>>>, k: Cont, is_loop: bool) -> Step {
    if v.len() != 3 {
        return k(Err("binding form expects bindings and body".into()));
    }
    let b = match bindings(&v, if is_loop { "loop" } else { "let" }) {
        Ok(x) => x,
        Err(x) => return k(Err(x)),
    };
    let names = Rc::new(
        b.chunks(2)
            .filter_map(|p| {
                if let Form::Symbol(n) = &p[0] {
                    Some(n.clone())
                } else {
                    None
                }
            })
            .collect(),
    );
    let body = v[2].clone();
    bind_values(
        Rc::new(b),
        0,
        Vec::new(),
        env,
        Box::new(move |r, e| match r {
            Ok(old) if is_loop => loop_body(names, body, old, e, k),
            Ok(old) => {
                let re = e.clone();
                one(
                    body,
                    e,
                    Box::new(move |r| {
                        restore(&mut re.borrow_mut(), old);
                        k(r)
                    }),
                )
            }
            Err(x) => k(Err(x)),
        }),
    )
}
fn loop_body(
    names: Rc<Vec<String>>,
    body: Form,
    old: Previous,
    env: Rc<RefCell<HashMap<String, Value>>>,
    k: Cont,
) -> Step {
    let nn = names.clone();
    let bb = body.clone();
    let oo = old.clone();
    let ee = env.clone();
    one(
        body,
        env,
        Box::new(move |r| match r {
            Ok(Value::Recur(v)) => {
                if v.len() != nn.len() {
                    restore(&mut ee.borrow_mut(), oo);
                    return k(Err("loop recur arity mismatch".into()));
                }
                for (n, x) in nn.iter().zip(v) {
                    ee.borrow_mut().insert(n.clone(), x);
                }
                loop_body(nn, bb, oo, ee, k)
            }
            r => {
                restore(&mut ee.borrow_mut(), oo);
                k(r)
            }
        }),
    )
}
fn bind_form(v: Vec<Form>, env: Rc<RefCell<HashMap<String, Value>>>, k: Cont) -> Step {
    if v.len() != 3 {
        return k(Err("binding form expects symbol and value".into()));
    }
    let op = match &v[0] {
        Form::Symbol(n) => n.clone(),
        _ => unreachable!(),
    };
    let name = match &v[1] {
        Form::Symbol(n) => n.clone(),
        _ => return k(Err("binding name must be a symbol".into())),
    };
    let e = env.clone();
    one(
        v[2].clone(),
        env,
        Box::new(move |r| match r {
            Ok(x) => {
                let mut env = e.borrow_mut();
                if op == "def" {
                    if let Some(Value::Var(c)) = env.get(&name) {
                        c.reset_value(x.clone());
                    } else {
                        env.insert(
                            name.clone(),
                            Value::Var(crate::kernel::Var::new(name, x.clone())),
                        );
                    }
                } else {
                    let Some(c) = binding_var(&mut env, &name) else {
                        return k(Err(format!("unbound var: {name}")));
                    };
                    c.reset_value(x.clone());
                }
                drop(env);
                k(Ok(x))
            }
            Err(x) => k(Err(x)),
        }),
    )
}

fn try_cps(v: Vec<Form>, env: Rc<RefCell<HashMap<String, Value>>>, k: Cont) -> Step {
    let mut body = Vec::new();
    let mut catch = None;
    let mut finals = Vec::new();
    for f in v.into_iter().skip(1) {
        match &f {
            Form::List(p) if !p.is_empty() && matches!(&p[0],Form::Symbol(n)if n=="catch") => {
                catch = Some(p.clone())
            }
            Form::List(p) if !p.is_empty() && matches!(&p[0],Form::Symbol(n)if n=="finally") => {
                finals.extend_from_slice(&p[1..])
            }
            _ if catch.is_none() => body.push(f),
            _ => return k(Err("try clauses must follow body".into())),
        }
    }
    let e = env.clone();
    forms_cps(
        Rc::new(body),
        0,
        Value::Nil,
        env,
        Box::new(move |r| finish_try(r, catch, finals, e, k)),
    )
}
fn finish_try(
    r: Result<Value, String>,
    catch: Option<Vec<Form>>,
    finals: Vec<Form>,
    env: Rc<RefCell<HashMap<String, Value>>>,
    k: Cont,
) -> Step {
    match (r, catch) {
        (Err(x), Some(p)) => {
            if p.len() != 3 {
                return k(Err("catch expects name and body".into()));
            }
            let n = match &p[1] {
                Form::Symbol(n) => n.clone(),
                _ => return k(Err("catch name must be symbol".into())),
            };
            let old = env.borrow_mut().insert(n.clone(), Value::String(x));
            let e = env.clone();
            one(
                p[2].clone(),
                env,
                Box::new(move |r| {
                    restore(&mut e.borrow_mut(), vec![(n, old)]);
                    finally(r, finals, e, k)
                }),
            )
        }
        (r, _) => finally(r, finals, env, k),
    }
}
fn finally(
    result: Result<Value, String>,
    v: Vec<Form>,
    env: Rc<RefCell<HashMap<String, Value>>>,
    k: Cont,
) -> Step {
    forms_cps(
        Rc::new(v),
        0,
        Value::Nil,
        env,
        Box::new(move |r| match r {
            Err(x) => k(Err(x)),
            Ok(_) => k(result),
        }),
    )
}

thread_local! {static TEMP:Cell<u64>=const{Cell::new(0)};}
fn temp() -> String {
    TEMP.with(|x| {
        let n = x.get();
        x.set(n + 1);
        format!("__fiber_{n}")
    })
}
fn application(v: Vec<Form>, env: Rc<RefCell<HashMap<String, Value>>>, k: Cont) -> Step {
    let f = match &v[0] {
        Form::Symbol(n) => binding_value(&env.borrow(), n),
        _ => None,
    };
    if let Some(Value::Function(f)) = f {
        return values_cps(
            Rc::new(v[1..].to_vec()),
            0,
            Vec::new(),
            env,
            Box::new(move |r| match r {
                Ok(a) => call(f, a, k),
                Err(x) => k(Err(x)),
            }),
        );
    }
    let op = v[0].clone();
    let e = env.clone();
    values_cps(
        Rc::new(v[1..].to_vec()),
        0,
        Vec::new(),
        env,
        Box::new(move |r| match r {
            Ok(values) => {
                let mut env = e.borrow_mut();
                let mut old = Vec::new();
                let mut list = vec![op];
                for x in values {
                    let n = temp();
                    let prior = env.insert(n.clone(), x);
                    old.push((n.clone(), prior));
                    list.push(Form::Symbol(n));
                }
                let r = eval(&Form::List(list), &mut env);
                restore(&mut env, old);
                drop(env);
                k(r)
            }
            Err(x) => k(Err(x)),
        }),
    )
}
fn call(f: Rc<Function>, args: Vec<Value>, k: Cont) -> Step {
    if f.variadic.is_none() && f.params.len() != args.len() {
        return k(Err(format!(
            "function expects {} arguments",
            f.params.len()
        )));
    }
    if args.len() < f.params.len() {
        return k(Err(format!(
            "function expects at least {} arguments",
            f.params.len()
        )));
    }
    let mut env = f.captured.borrow().clone();
    for (n, x) in f.params.iter().zip(args.iter()) {
        env.insert(n.clone(), x.clone());
    }
    if let Some(n) = &f.variadic {
        let skip = f.params.len();
        env.insert(
            n.clone(),
            Value::List(args.into_iter().skip(skip).collect()),
        );
    }
    forms_cps(
        Rc::new(f.body.clone()),
        0,
        Value::Nil,
        Rc::new(RefCell::new(env)),
        Box::new(move |r| match r {
            Ok(Value::Recur(_)) => k(Err("recur must be inside loop".into())),
            r => k(r),
        }),
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn resumes_nested() {
        let p = Promise::new();
        let mut e = HashMap::new();
        e.insert("p".into(), Value::Promise(p.clone()));
        let mut f = EvalFiber::start("(let [x 1] (+ x (deref p)))", e).unwrap();
        assert_eq!(f.state(), EvalFiberState::Suspended);
        p.resolve(Value::Number(41));
        assert_eq!(
            f.resume(p.state()),
            EvalFiberState::Completed(Value::Number(42))
        );
    }
    #[test]
    fn resumes_function_finally() {
        let p = Promise::new();
        let mut e = HashMap::new();
        e.insert("p".into(), Value::Promise(p.clone()));
        let mut f = EvalFiber::start(
            "(do (def f (fn [x] (try (+ x (deref p)) (finally nil)))) (f 2))",
            e,
        )
        .unwrap();
        assert_eq!(f.state(), EvalFiberState::Suspended);
        p.resolve(Value::Number(40));
        assert_eq!(
            f.resume(p.state()),
            EvalFiberState::Completed(Value::Number(42))
        );
    }
}
