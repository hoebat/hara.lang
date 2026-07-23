package hara.lang.test;

import hara.truffle.HaraFunction;

public record HaraTestCase(String namespace, String name, Object metadata, HaraFunction body) {}
