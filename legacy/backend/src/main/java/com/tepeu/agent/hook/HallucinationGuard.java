package com.tepeu.agent.hook;

import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 最小幻觉门禁：写路径父目录须存在；扫描助手文本中「已写入/创建」的路径是否真实存在。
 * Spec L6 / M2.3「幻觉检测」最小实现。
 * 关联：WriteFileTool、FileController、ChatController。
 */
@Component
public class HallucinationGuard {

    /** 中英文「已创建/写入/保存了 xxx」类声明 */
    private static final Pattern CLAIM = Pattern.compile(
            "(?i)(?:已(?:经)?(?:创建|写入|保存|生成)|created|wrote|written|saved)\\s+"
                    + "[`\"']?([\\w./\\\\-]+\\.[\\w]+)[`\"']?");

    /**
     * 写文件前：目标父目录必须已存在（工作区根下直接写文件除外）。
     * @return null 表示通过；否则为错误信息
     */
    public String checkWriteParent(Path target) {
        if (target == null) {
            return "ERROR: HALLUCINATION 无效路径";
        }
        Path parent = target.getParent();
        if (parent == null) {
            return null;
        }
        if (Files.isDirectory(parent)) {
            return null;
        }
        return "ERROR: HALLUCINATION 父目录不存在：" + parent.getFileName();
    }

    /**
     * 从助手文本提取声称已写入的相对路径，返回工作区中不存在的那些。
     */
    public List<String> findMissingClaimedPaths(String assistantText, Path workspaceRoot) {
        List<String> missing = new ArrayList<>();
        if (assistantText == null || assistantText.isBlank() || workspaceRoot == null) {
            return missing;
        }
        Set<String> seen = new LinkedHashSet<>();
        Matcher m = CLAIM.matcher(assistantText);
        while (m.find()) {
            String rel = m.group(1);
            if (rel == null || rel.isBlank()) continue;
            rel = rel.replace('\\', '/');
            if (rel.contains("..")) continue;
            if (!seen.add(rel.toLowerCase(Locale.ROOT))) continue;
            Path resolved = workspaceRoot.resolve(rel).normalize();
            if (!resolved.startsWith(workspaceRoot.normalize())) continue;
            if (!Files.exists(resolved)) {
                missing.add(rel);
            }
        }
        return missing;
    }
}
