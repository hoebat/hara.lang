#include "simdjson.h"
#include <iostream>

extern "C" {
    double parse(const char* json) {
        simdjson::dom::parser parser;
        simdjson::dom::element doc = parser.parse(json, strlen(json));
        return doc["pi"];
    }
}
