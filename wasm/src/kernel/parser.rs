use super::form::Form;
use super::reader::{Position, Reader};

fn canonical_decimal(value: &str) -> String {
    let (mantissa, exponent) = value
        .split_once(['e', 'E'])
        .map_or((value, None), |(m, e)| (m, Some(e)));
    let mut mantissa = mantissa.to_owned();
    if mantissa.contains('.') {
        while mantissa.ends_with('0') {
            mantissa.pop();
        }
        if mantissa.ends_with('.') {
            mantissa.pop();
        }
    }
    if mantissa == "-0" || mantissa == "+0" {
        mantissa = "0".into();
    }
    exponent.map_or(mantissa.clone(), |e| format!("{mantissa}E{e}"))
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Span {
    pub start: Position,
    pub end: Position,
}
#[derive(Debug, Clone, PartialEq)]
pub struct SpannedForm {
    pub form: Form,
    pub span: Span,
}
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ParseError {
    pub message: String,
    pub position: Position,
}
impl std::fmt::Display for ParseError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(
            f,
            "{} [line {}, column {}]",
            self.message, self.position.line, self.position.column
        )
    }
}
impl std::error::Error for ParseError {}

type Result<T> = std::result::Result<T, ParseError>;
pub struct Parser<'a> {
    reader: Reader<'a>,
}
impl<'a> Parser<'a> {
    pub fn new(source: &'a str) -> Self {
        Self {
            reader: Reader::new(source),
        }
    }
    fn error<T>(&self, message: impl Into<String>) -> Result<T> {
        Err(ParseError {
            message: message.into(),
            position: self.reader.position(),
        })
    }
    fn whitespace(&mut self) {
        loop {
            self.reader.read_while(|ch| ch.is_whitespace() || ch == ',');
            if self.reader.peek_char() == Some(';') {
                self.reader.read_until(|ch| ch == '\n');
            } else {
                break;
            }
        }
    }
    fn token(&mut self, first: char) -> String {
        let mut token = String::from(first);
        token.push_str(
            &self
                .reader
                .read_while(|ch| !ch.is_whitespace() && ch != ',' && !"()[]{}\";".contains(ch)),
        );
        token
    }
    fn string(&mut self) -> Result<String> {
        let mut out = String::new();
        loop {
            match self.reader.read_char() {
                None => return self.error("EOF while reading string"),
                Some('"') => return Ok(out),
                Some('\\') => {
                    let escaped = self.reader.read_char().ok_or_else(|| ParseError {
                        message: "EOF while reading string escape".into(),
                        position: self.reader.position(),
                    })?;
                    match escaped {
                        'n' => out.push('\n'),
                        'r' => out.push('\r'),
                        't' => out.push('\t'),
                        'b' => out.push('\u{0008}'),
                        'f' => out.push('\u{000c}'),
                        '\\' => out.push('\\'),
                        '"' => out.push('"'),
                        'u' => {
                            let mut digits = String::new();
                            for _ in 0..4 {
                                let digit = self.reader.read_char().ok_or_else(|| ParseError {
                                    message: "EOF in Unicode escape".into(),
                                    position: self.reader.position(),
                                })?;
                                if !digit.is_ascii_hexdigit() {
                                    return self.error(format!("Invalid digit: {digit}"));
                                }
                                digits.push(digit);
                            }
                            let value = u32::from_str_radix(&digits, 16).expect("validated hex");
                            out.push(char::from_u32(value).ok_or_else(|| ParseError {
                                message: "Invalid Unicode scalar".into(),
                                position: self.reader.position(),
                            })?);
                        }
                        first @ '0'..='7' => {
                            let mut digits = String::from(first);
                            for _ in 0..2 {
                                match self.reader.peek_char() {
                                    Some(next @ '0'..='7') => {
                                        self.reader.read_char();
                                        digits.push(next);
                                    }
                                    _ => break,
                                }
                            }
                            let value = u32::from_str_radix(&digits, 8).expect("validated octal");
                            out.push(char::from_u32(value).ok_or_else(|| ParseError {
                                message: "Invalid octal scalar".into(),
                                position: self.reader.position(),
                            })?);
                        }
                        other => return self.error(format!("unsupported escape: \\{other}")),
                    }
                }
                Some(ch) => out.push(ch),
            }
        }
    }
    fn regex(&mut self) -> Result<String> {
        let mut out = String::new();
        loop {
            match self.reader.read_char() {
                None => return self.error("EOF while reading regex"),
                Some('"') => return Ok(out),
                Some('\\') => {
                    out.push('\\');
                    out.push(self.reader.read_char().ok_or_else(|| ParseError {
                        message: "EOF while reading regex".into(),
                        position: self.reader.position(),
                    })?);
                }
                Some(ch) => out.push(ch),
            }
        }
    }

    fn metadata(&self, meta: Form, value: Form) -> Result<Form> {
        let normalized = match meta {
            Form::Keyword(name) => Form::Map(vec![(Form::Keyword(name), Form::Bool(true))]),
            tag @ (Form::Symbol(_) | Form::String(_)) => {
                Form::Map(vec![(Form::Keyword("tag".into()), tag)])
            }
            map @ Form::Map(_) => map,
            _ => return self.error("Metadata must be Symbol, Keyword, String or Map"),
        };
        if !matches!(
            value,
            Form::List(_) | Form::Vector(_) | Form::Map(_) | Form::Set(_)
        ) {
            return self.error("Metadata can only be applied to object forms");
        }
        Ok(Form::Metadata(Box::new(normalized), Box::new(value)))
    }
    fn delimited(&mut self, close: char, kind: &str) -> Result<Vec<Form>> {
        let mut forms = Vec::new();
        loop {
            self.whitespace();
            match self.reader.peek_char() {
                None => return self.error(format!("EOF while reading {kind}")),
                Some(ch) if ch == close => {
                    self.reader.read_char();
                    return Ok(forms);
                }
                _ => match self.read_one()? {
                    Some(form) => forms.push(form.form),
                    None => {}
                },
            }
        }
    }
    fn prefixed(&mut self, name: &str) -> Result<Form> {
        let value = self.read_required(name)?;
        Ok(Form::List(vec![Form::Symbol(name.into()), value.form]))
    }
    fn read_required(&mut self, context: &str) -> Result<SpannedForm> {
        self.whitespace();
        self.read_one()?.ok_or_else(|| ParseError {
            message: format!("EOF after {context}"),
            position: self.reader.position(),
        })
    }
    fn dispatch(&mut self) -> Result<Option<Form>> {
        match self.reader.read_char() {
            Some('{') => {
                let forms = self.delimited('}', "set")?;
                if forms
                    .iter()
                    .enumerate()
                    .any(|(i, value)| forms[..i].contains(value))
                {
                    return self.error("Duplicate item");
                }
                Ok(Some(Form::Set(forms)))
            }
            Some('_') => {
                self.read_required("#_")?;
                Ok(None)
            }
            Some('\'') => {
                let value = self.read_required("var quote")?;
                if !matches!(value.form, Form::Symbol(_)) {
                    return self.error("Var quote expects a symbol");
                }
                Ok(Some(Form::List(vec![
                    Form::Symbol("var".into()),
                    value.form,
                ])))
            }
            Some('"') => Ok(Some(Form::Regex(self.regex()?))),
            Some('<') => self.error("Unreadable form"),
            Some('^') => {
                let meta = self.read_required("metadata")?;
                let value = self.read_required("metadata")?;
                Ok(Some(self.metadata(meta.form, value.form)?))
            }
            Some('#') => {
                let value = self.read_required("symbolic value")?;
                match value.form {
                    Form::Symbol(name) if name == "Inf" => Ok(Some(Form::Float(f64::INFINITY))),
                    Form::Symbol(name) if name == "-Inf" => {
                        Ok(Some(Form::Float(f64::NEG_INFINITY)))
                    }
                    Form::Symbol(name) if name == "NaN" => Ok(Some(Form::Float(f64::NAN))),
                    Form::Symbol(name) => self.error(format!("Unknown symbolic value: ##{name}")),
                    _ => self.error("Invalid symbolic value"),
                }
            }
            Some('[') => self.error("No dispatch macro for: ["),
            Some(ch) => {
                if !ch.is_alphabetic() {
                    return self.error(format!("No dispatch macro for: {ch}"));
                }
                let tag = self.token(ch);
                if tag.is_empty() {
                    return self.error(format!("No dispatch macro for: {ch}"));
                }
                let value = self.read_required("tagged literal")?;
                Ok(Some(Form::Tagged(tag, Box::new(value.form))))
            }
            None => self.error("EOF after #"),
        }
    }
    fn atom(&self, token: String) -> Result<Form> {
        match token.as_str() {
            "nil" => return Ok(Form::Nil),
            "true" => return Ok(Form::Bool(true)),
            "false" => return Ok(Form::Bool(false)),
            _ => {}
        }
        if let Some(keyword) = token.strip_prefix(':') {
            if keyword.is_empty() || keyword == "/" {
                return self.error(format!("Keyword not allowed: {token}"));
            }
            return Ok(Form::Keyword(keyword.into()));
        }
        let body = token.strip_prefix(['+', '-']).unwrap_or(&token);
        if body.contains('/')
            && body.split_once('/').is_some_and(|(n, d)| {
                !n.is_empty()
                    && !d.is_empty()
                    && n.chars().all(|ch| ch.is_ascii_digit())
                    && d.chars().all(|ch| ch.is_ascii_digit())
            })
        {
            return self.error("Ratios are not supported");
        }
        let numeric = body.chars().next().is_some_and(|ch| ch.is_ascii_digit());
        if numeric {
            let negative = token.starts_with('-');
            let sign = if negative { "-" } else { "" };
            let parsed =
                if let Some(hex) = body.strip_prefix("0x").or_else(|| body.strip_prefix("0X")) {
                    i64::from_str_radix(hex, 16)
                        .ok()
                        .map(|value| Form::Number(if negative { -value } else { value }))
                } else if let Some(big) = body.strip_suffix('N') {
                    big.chars()
                        .all(|ch| ch.is_ascii_digit())
                        .then(|| Form::BigInteger(format!("{sign}{big}")))
                } else if let Some(decimal) = body.strip_suffix('M') {
                    decimal
                        .parse::<f64>()
                        .ok()
                        .map(|_| Form::Decimal(canonical_decimal(&format!("{sign}{decimal}"))))
                } else if let Some((radix, digits)) = body.split_once(['r', 'R']) {
                    radix
                        .parse::<u32>()
                        .ok()
                        .filter(|radix| (2..=36).contains(radix))
                        .and_then(|radix| i64::from_str_radix(digits, radix).ok())
                        .map(|value| Form::Number(if negative { -value } else { value }))
                } else if body.len() > 1
                    && body.starts_with('0')
                    && body.chars().all(|ch| ('0'..='7').contains(&ch))
                {
                    i64::from_str_radix(body, 8)
                        .ok()
                        .map(|value| Form::Number(if negative { -value } else { value }))
                } else if body.contains(['.', 'e', 'E']) {
                    token.parse::<f64>().ok().map(Form::Float)
                } else {
                    token.parse::<i64>().ok().map(Form::Number).or_else(|| {
                        body.chars()
                            .all(|ch| ch.is_ascii_digit())
                            .then(|| Form::BigInteger(token.clone()))
                    })
                };
            return parsed.ok_or_else(|| ParseError {
                message: format!("Invalid number: {token}"),
                position: self.reader.position(),
            });
        }
        if token.contains('/') && token.chars().filter(|ch| *ch == '/').count() > 1 {
            return self.error(format!("Invalid token: {token}"));
        }
        Ok(Form::Symbol(token))
    }
    fn read_one(&mut self) -> Result<Option<SpannedForm>> {
        self.whitespace();
        if self.reader.is_eof() {
            return Ok(None);
        }
        let start = self.reader.position();
        let ch = self.reader.read_char().expect("checked EOF");
        let form = match ch {
            '(' => Some(Form::List(self.delimited(')', "list")?)),
            '[' => Some(Form::Vector(self.delimited(']', "vector")?)),
            '{' => {
                let forms = self.delimited('}', "map")?;
                if forms.len() % 2 != 0 {
                    return self.error("Map literal requires an even number of forms");
                }
                {
                    let entries = forms
                        .chunks(2)
                        .map(|p| (p[0].clone(), p[1].clone()))
                        .collect::<Vec<_>>();
                    if entries
                        .iter()
                        .enumerate()
                        .any(|(i, (key, _))| entries[..i].iter().any(|(prior, _)| prior == key))
                    {
                        return self.error("Duplicate key");
                    }
                    Some(Form::Map(entries))
                }
            }
            ')' | ']' | '}' => return self.error(format!("Unmatched delimiter: {ch}")),
            '"' => Some(Form::String(self.string()?)),
            '\'' => Some(self.prefixed("quote")?),
            '@' => Some(self.prefixed("deref")?),
            '`' => Some(self.prefixed("syntax-quote")?),
            '~' => {
                if self.reader.peek_char() == Some('@') {
                    self.reader.read_char();
                    Some(self.prefixed("unquote-splicing")?)
                } else {
                    Some(self.prefixed("unquote")?)
                }
            }
            '^' => {
                let meta = self.read_required("metadata")?;
                let value = self.read_required("metadata")?;
                Some(self.metadata(meta.form, value.form)?)
            }
            '\\' => {
                let token = self
                    .reader
                    .read_while(|c| !c.is_whitespace() && !"()[]{}".contains(c));
                let value = match token.as_str() {
                    "newline" => Some('\n'),
                    "space" => Some(' '),
                    "tab" => Some('\t'),
                    "backspace" => Some('\u{0008}'),
                    "formfeed" => Some('\u{000c}'),
                    "return" => Some('\r'),
                    _ if token.starts_with('u') && token.len() == 5 => {
                        u32::from_str_radix(&token[1..], 16)
                            .ok()
                            .and_then(char::from_u32)
                    }
                    _ if token.starts_with('o') && (2..=4).contains(&token.len()) => {
                        u32::from_str_radix(&token[1..], 8)
                            .ok()
                            .and_then(char::from_u32)
                    }
                    _ if token.chars().count() == 1 => token.chars().next(),
                    _ => None,
                };
                Some(Form::Character(value.ok_or_else(|| ParseError {
                    message: format!("Invalid character: \\{token}"),
                    position: self.reader.position(),
                })?))
            }
            '#' => self.dispatch()?,
            other => {
                let token = self.token(other);
                Some(self.atom(token)?)
            }
        };
        Ok(form.map(|form| SpannedForm {
            form,
            span: Span {
                start,
                end: self.reader.position(),
            },
        }))
    }
    pub fn read_all(mut self) -> Result<Vec<SpannedForm>> {
        let mut forms = Vec::new();
        loop {
            self.whitespace();
            if self.reader.is_eof() {
                break;
            }
            if let Some(form) = self.read_one()? {
                forms.push(form);
            }
        }
        if forms.is_empty() {
            return self.error("source contains no forms");
        }
        Ok(forms)
    }
}
pub fn read_forms(source: &str) -> Result<Vec<SpannedForm>> {
    Parser::new(source).read_all()
}
pub fn parse_forms(source: &str) -> std::result::Result<Vec<Form>, String> {
    read_forms(source)
        .map(|forms| forms.into_iter().map(|f| f.form).collect())
        .map_err(|e| e.to_string())
}
pub fn parse(source: &str) -> std::result::Result<Form, String> {
    let mut forms = parse_forms(source)?;
    if forms.len() != 1 {
        return Err("source contains multiple forms; use eval_text".into());
    }
    Ok(forms.remove(0))
}

#[cfg(test)]
mod tests {
    use super::{parse_forms, read_forms};
    use crate::kernel::Form;
    #[test]
    fn tracks_spans_comments_commas_and_reader_macros() {
        let forms = read_forms("; hi\n[1, 2] #'x #_gone :ok").unwrap();
        assert_eq!(forms.len(), 3);
        assert_eq!(forms[0].span.start.line, 2);
        assert!(
            matches!(&forms[1].form,Form::List(v)if matches!(&v[0],Form::Symbol(s)if s=="var"))
        );
        assert_eq!(forms[2].form, Form::Keyword("ok".into()));
    }
    #[test]
    fn reports_delimited_errors_with_position() {
        let error = parse_forms("[1\n2").unwrap_err();
        assert!(error.contains("line 2"));
        assert!(error.contains("EOF while reading vector"));
    }

    #[test]
    fn matches_canonical_numbers_characters_and_duplicate_errors() {
        assert_eq!(
            parse_forms("123 123.45 123N 123.45M 0xFF 2r1010").unwrap(),
            vec![
                Form::Number(123),
                Form::Float(123.45),
                Form::BigInteger("123".into()),
                Form::Decimal("123.45".into()),
                Form::Number(255),
                Form::Number(10)
            ]
        );
        assert_eq!(
            parse_forms("\\newline \\u03bb \\o377").unwrap(),
            vec![
                Form::Character('\n'),
                Form::Character('λ'),
                Form::Character('ÿ')
            ]
        );
        assert!(parse_forms("{:a 1 :a 2}")
            .unwrap_err()
            .contains("Duplicate key"));
        assert!(parse_forms("#{1 1}")
            .unwrap_err()
            .contains("Duplicate item"));
        assert!(parse_forms("123a").unwrap_err().contains("Invalid number"));
    }

    #[test]
    fn preserves_regex_tagged_and_extended_string_literals() {
        assert_eq!(
            parse_forms("#\"abc\" #math[:tensor 42]").unwrap(),
            vec![
                Form::Regex("abc".into()),
                Form::Tagged(
                    "math".into(),
                    Box::new(Form::Vector(vec![
                        Form::Keyword("tensor".into()),
                        Form::Number(42)
                    ]))
                )
            ]
        );
        assert_eq!(
            parse_forms("\"\\t\\r\\n\\b\\f\\\\\\\"\\7\"").unwrap(),
            vec![Form::String("\t\r\n\u{0008}\u{000c}\\\"\u{0007}".into())]
        );
    }
    #[test]
    fn preserves_metadata_and_rejects_unknown_dispatch_forms() {
        assert_eq!(
            parse_forms("^:private [1]").unwrap(),
            vec![Form::Metadata(
                Box::new(Form::Map(vec![(
                    Form::Keyword("private".into()),
                    Form::Bool(true)
                )])),
                Box::new(Form::Vector(vec![Form::Number(1)]))
            )]
        );
        assert!(parse_forms("#[1 2]")
            .unwrap_err()
            .contains("No dispatch macro for: ["));
    }
    #[test]
    fn matches_extended_canonical_reader_categories() {
        assert_eq!(
            parse_forms("1.00M 9223372036854775808 123N").unwrap(),
            vec![
                Form::Decimal("1".into()),
                Form::BigInteger("9223372036854775808".into()),
                Form::BigInteger("123".into())
            ]
        );
        assert!(parse_forms("1/2")
            .unwrap_err()
            .contains("Ratios are not supported"));
        assert_eq!(
            parse_forms(r##"#"\d+""##).unwrap(),
            vec![Form::Regex(r"\d+".into())]
        );
        let symbolic = parse_forms("##Inf ##-Inf ##NaN").unwrap();
        assert!(matches!(symbolic[0], Form::Float(value) if value == f64::INFINITY));
        assert!(matches!(symbolic[1], Form::Float(value) if value == f64::NEG_INFINITY));
        assert!(matches!(symbolic[2], Form::Float(value) if value.is_nan()));
        assert!(parse_forms("#'1")
            .unwrap_err()
            .contains("Var quote expects a symbol"));
        assert!(parse_forms("^1 [2]")
            .unwrap_err()
            .contains("Metadata must be"));
        assert!(parse_forms("^:private 1")
            .unwrap_err()
            .contains("Metadata can only be applied"));
    }
    #[test]
    fn supports_dispatch_and_quote_forms() {
        let forms = parse_forms("#{1 2} 'x @y #tag {:a 1}").unwrap();
        assert_eq!(forms.len(), 4);
        assert!(matches!(forms[0], Form::Set(_)));
        assert!(matches!(&forms[3], Form::Tagged(tag, _) if tag == "tag"));
    }
}
