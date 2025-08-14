final class Captures {
    int start = -1, end = -1;
    boolean isSet() { return start >= 0; }
    int length() { return isSet() ? (end - start) : 0; }
    Captures copy() { Captures c = new Captures(); c.start = start; c.end = end; return c; }
}
