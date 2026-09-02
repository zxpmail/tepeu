package com.tepeu.os.orchestration.conformance;

import com.tepeu.os.conformance.ConformanceCase;
import com.tepeu.os.orchestration.AssembledPrompt;
import com.tepeu.os.orchestration.local.PromptAssembly;
import com.tepeu.os.orchestration.Section;
import com.tepeu.os.orchestration.SectionKind;

import java.util.ArrayList;
import java.util.List;

import static com.tepeu.os.conformance.ConformanceCheck.check;
import static com.tepeu.os.conformance.ConformanceCheck.checkEquals;
import static com.tepeu.os.conformance.ConformanceCheck.expectThrows;

/**
 * PromptAssembly 套件（测试）：注册序、静/动分离、超预算出账单。不进发行 jar。
 */
public final class PromptConformance {

    private PromptConformance() {
    }

    public static List<ConformanceCase> suite() {
        List<ConformanceCase> cases = new ArrayList<>();
        cases.add(new ConformanceCase("prompt", "注册序冻住，重复 id 拒绝",
                () -> {
                    PromptAssembly a = new PromptAssembly();
                    a.register(Section.stat("base", "A"));
                    a.register(Section.dyn("task_brief", "B"));
                    checkEquals(List.of("base", "task_brief"), a.registeredIds(), "序");
                    expectThrows(IllegalStateException.class,
                            () -> a.register(Section.stat("base", "X")));
                }));
        cases.add(new ConformanceCase("prompt", "STATIC 进 system，DYNAMIC 不混进 system",
                () -> {
                    PromptAssembly a = new PromptAssembly();
                    a.register(Section.stat("base", "identity"));
                    a.register(Section.dyn("task_brief", "do-x"));
                    AssembledPrompt p = a.assemble(100);
                    checkEquals("identity", p.system(), "system");
                    checkEquals(List.of("do-x"), p.dynamicBodies(), "dynamic");
                    checkEquals(List.of("base", "task_brief"), p.includedIds(), "ids");
                    check(p.bill().isEmpty(), "未超预算");
                }));
        cases.add(new ConformanceCase("prompt", "未注册的段不出现（无记忆平面则无 memory_hits）",
                () -> {
                    PromptAssembly a = new PromptAssembly();
                    a.register(Section.stat("base", "ok"));
                    AssembledPrompt p = a.assemble(100);
                    check(!p.includedIds().contains("memory_hits"), "不得捏造");
                    checkEquals("ok", p.system(), "system");
                }));
        cases.add(new ConformanceCase("prompt", "超预算先丢宽泛 DYNAMIC，账单列出路径",
                () -> {
                    PromptAssembly a = new PromptAssembly();
                    a.register(Section.stat("base", "ID"));
                    a.register(Section.dyn("skills", "CATALOG-WIDE"));
                    a.register(Section.dyn("task_brief", "SPECIFIC"));
                    AssembledPrompt p = a.assemble(2 + 8);
                    checkEquals("ID", p.system(), "前缀保住");
                    checkEquals(List.of("SPECIFIC"), p.dynamicBodies(), "留最具体");
                    checkEquals(1, p.bill().size(), "一笔省略");
                    checkEquals("skills", p.bill().get(0).sectionId(), "丢宽泛");
                    checkEquals("DROP", p.bill().get(0).reason(), "DROP");
                }));
        cases.add(new ConformanceCase("prompt", "STATIC 超预算则截断并出账单",
                () -> {
                    PromptAssembly a = new PromptAssembly();
                    a.register(Section.stat("base", "ABCDEFGH"));
                    AssembledPrompt p = a.assemble(3);
                    checkEquals("ABC", p.system(), "截前缀");
                    checkEquals("TRUNCATE", p.bill().get(0).reason(), "TRUNCATE");
                    checkEquals("base", p.bill().get(0).sectionId(), "id");
                }));
        cases.add(new ConformanceCase("prompt", "技能目录仅 name/description/digest",
                () -> {
                    Section cat = PromptAssembly.skillCatalog(List.of(
                            new PromptAssembly.SkillRef("echo", "repeat", "deadbeef")));
                    checkEquals("skills", cat.id(), "id");
                    check(cat.body().contains("echo"), "name");
                    check(cat.body().contains("deadbeef"), "digest");
                    check(!cat.body().contains("function body"), "不得夹正文");
                }));
        cases.add(new ConformanceCase("prompt", "memory_hits 须有 KnowledgeSource 命中且带 sourceId",
                () -> {
                    PromptAssembly a = new PromptAssembly();
                    a.register(Section.stat("base", "ok"));
                    a.register(PromptAssembly.memoryHits(List.of(
                            new com.tepeu.os.orchestration.KnowledgeSource.Hit("doc:1", "tepeu rule"))));
                    AssembledPrompt p = a.assemble(200);
                    check(p.includedIds().contains("memory_hits"), "应收录");
                    checkEquals("doc:1\ttepeu rule", p.dynamicBodies().get(0), "正文");
                }));
        cases.add(new ConformanceCase("prompt", "intoModel=false 不进组装",
                () -> {
                    PromptAssembly a = new PromptAssembly();
                    a.register(new Section("hidden", SectionKind.STATIC, "nope", false));
                    AssembledPrompt p = a.assemble(100);
                    checkEquals("", p.system(), "空");
                    check(p.includedIds().isEmpty(), "未收录");
                }));
        return List.copyOf(cases);
    }
}
