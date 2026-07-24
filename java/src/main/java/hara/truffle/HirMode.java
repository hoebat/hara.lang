package hara.truffle;

enum HirMode {
  AUTO,
  STRICT,
  OFF;

  static HirMode current() {
    String value = System.getProperty("hara.HirMode", "auto");
    return switch (value.toLowerCase(java.util.Locale.ROOT)) {
      case "auto" -> AUTO;
      case "strict" -> STRICT;
      case "off" -> OFF;
      default -> throw new HaraException(
          "hara.HirMode expects auto, strict, or off; received " + value);
    };
  }
}
