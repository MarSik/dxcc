package org.marsik.ham.dxcc;

import java.time.Instant;
import java.util.List;

import lombok.Value;

@Value
public class Dxcc {
    int id;

    String prefix;
    String arrlFlags;

    String name;
    List<String> cq;
    List<String> itu;

    List<String> continent;

    boolean deleted;
    Instant onlyAfter; // after and including
    Instant onlyBefore; // before, excluding

    List<Integer> notes;
}
