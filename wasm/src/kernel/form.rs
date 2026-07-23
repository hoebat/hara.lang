#[derive(Debug, Clone, PartialEq)]
pub enum Form {
    Number(i64),
    Symbol(String),
    Keyword(String),
    String(String),
    Map(Vec<(Form, Form)>),
    Set(Vec<Form>),
    Vector(Vec<Form>),
    List(Vec<Form>),
}
