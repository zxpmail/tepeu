package com.tepeu.os.orchestration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 按有序 Section 组装。禁止 Orchestrator 巨型字符串拼接（红线 §6-3）。
 * 超预算：先丢宽泛 DYNAMIC，后截最具体；账单列出省略路径。
 * 字符数当 weight（不是 tokenizer）。
 */
public final class PromptAssembly {

    private final List<Section> sections = new ArrayList<>();

    public PromptAssembly register(Section section) {
        Objects.requireNonNull(section, "section");
        for (Section existing : sections) {
            if (existing.id().equals(section.id())) {
                throw new IllegalStateException("section already registered: " + section.id());
            }
        }
        sections.add(section);
        return this;
    }

    public List<String> registeredIds() {
        List<String> ids = new ArrayList<>(sections.size());
        for (Section section : sections) {
            ids.add(section.id());
        }
        return List.copyOf(ids);
    }

    public AssembledPrompt assemble(int budgetChars) {
        if (budgetChars < 0) {
            throw new IllegalArgumentException("budgetChars < 0");
        }
        List<Section> stat = new ArrayList<>();
        List<Section> dyn = new ArrayList<>();
        for (Section section : sections) {
            if (!section.intoModel()) {
                continue;
            }
            if (section.kind() == SectionKind.STATIC) {
                stat.add(section);
            } else {
                dyn.add(section);
            }
        }
        List<Omission> bill = new ArrayList<>();
        List<Section> keptStat = pack(stat, budgetChars, bill, true);
        int used = weightOf(keptStat);
        int remain = budgetChars - used;
        List<Section> keptDyn = packDynamic(dyn, remain, bill);
        List<String> included = new ArrayList<>();
        for (Section section : keptStat) {
            included.add(section.id());
        }
        for (Section section : keptDyn) {
            included.add(section.id());
        }
        List<String> dynBodies = new ArrayList<>();
        for (Section section : keptDyn) {
            dynBodies.add(section.body());
        }
        return new AssembledPrompt(join(keptStat), dynBodies, included, bill);
    }

    /**
     * 技能目录段：仅 name+description+digest，正文另注册。
     */
    public static Section skillCatalog(List<SkillRef> refs) {
        Objects.requireNonNull(refs, "refs");
        StringBuilder sb = new StringBuilder();
        for (SkillRef ref : refs) {
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(ref.name()).append('\t').append(ref.description()).append('\t').append(ref.digest());
        }
        return Section.stat("skills", sb.toString());
    }

    private static List<Section> pack(List<Section> source, int budget, List<Omission> bill, boolean stopAfterMiss) {
        List<Section> kept = new ArrayList<>();
        int used = 0;
        boolean missed = false;
        for (Section section : source) {
            if (missed) {
                bill.add(new Omission(section.id(), "DROP"));
                continue;
            }
            int weight = section.weight();
            if (used + weight <= budget) {
                kept.add(section);
                used += weight;
                continue;
            }
            int room = budget - used;
            if (room > 0) {
                kept.add(new Section(section.id(), section.kind(), section.body().substring(0, room), true));
                bill.add(new Omission(section.id(), "TRUNCATE"));
                used = budget;
            } else {
                bill.add(new Omission(section.id(), "DROP"));
            }
            if (stopAfterMiss) {
                missed = true;
            }
        }
        return kept;
    }

    /** DYNAMIC：从后往前（最具体优先）；装不下则截最具体，更宽泛的 DROP。 */
    private static List<Section> packDynamic(List<Section> dyn, int remain, List<Omission> bill) {
        List<Section> reverseKept = new ArrayList<>();
        boolean closed = false;
        for (int i = dyn.size() - 1; i >= 0; i--) {
            Section section = dyn.get(i);
            if (closed) {
                bill.add(new Omission(section.id(), "DROP"));
                continue;
            }
            int weight = section.weight();
            if (weight <= remain) {
                reverseKept.add(section);
                remain -= weight;
                continue;
            }
            if (remain > 0) {
                reverseKept.add(new Section(section.id(), section.kind(), section.body().substring(0, remain), true));
                bill.add(new Omission(section.id(), "TRUNCATE"));
                remain = 0;
            } else {
                bill.add(new Omission(section.id(), "DROP"));
            }
            closed = true;
        }
        List<Section> kept = new ArrayList<>(reverseKept.size());
        for (int i = reverseKept.size() - 1; i >= 0; i--) {
            kept.add(reverseKept.get(i));
        }
        return kept;
    }

    private static int weightOf(List<Section> sections) {
        int n = 0;
        for (Section section : sections) {
            n += section.weight();
        }
        return n;
    }

    private static String join(List<Section> sections) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sections.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(sections.get(i).body());
        }
        return sb.toString();
    }

    public record SkillRef(String name, String description, String digest) {
        public SkillRef {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(digest, "digest");
        }
    }
}
