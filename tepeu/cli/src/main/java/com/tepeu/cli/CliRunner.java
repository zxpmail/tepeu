package com.tepeu.cli;

import com.tepeu.runtime.work.AskModel;
import com.tepeu.runtime.work.EditFile;
import com.tepeu.runtime.work.GlobFiles;
import com.tepeu.runtime.work.GrepFiles;
import com.tepeu.runtime.work.Job;
import com.tepeu.runtime.work.ReadFile;
import com.tepeu.runtime.work.RunProgram;
import com.tepeu.runtime.work.WriteFile;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 不带参数就停在这里一问一答。三个参数就只跑一轮正题。
 * 参数个数不对就只打印用法，不去问模型。
 */
@Component
public class CliRunner implements ApplicationRunner, ExitCodeGenerator {

    private int code;

    /** 这一轮能用的能力。读和写用模型认得的名字，到检查口再对上原来的两项。 */
    static List<Job> jobs(PrintStream notice) {
        return assemble(
                List.of(
                        AskModel::mimo,
                        ReadFile::new,
                        GrepFiles::new,
                        GlobFiles::new,
                        EditFile::new,
                        WriteFile::new,
                        RunProgram::new),
                notice);
    }

    /** 说明读不出来的那一个，启动时就不算交进来，其余照常。挡了要给人留一句话。 */
    static List<Job> assemble(List<Supplier<Job>> makes, PrintStream notice) {
        List<Job> built = new ArrayList<>();
        for (Supplier<Job> make : makes) {
            try {
                built.add(make.get());
            } catch (RuntimeException ex) {
                if (notice != null) {
                    notice.println("有一项没装上，这一轮不用它：" + ex.getMessage());
                }
            }
        }
        return List.copyOf(built);
    }

    /** 键盘和屏幕用同一种编码。Java 默认按 UTF-8 读，Windows 控制台实际不是。 */
    static Charset keyboardCharset(Charset screen) {
        return screen;
    }

    /** 没有参数就对话。三个字分别是要做什么、限制、怎样算做完。 */
    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<String> rest = args.getNonOptionArgs();
        try {
            if (rest.isEmpty()) {
                RoundRun.talk(
                        new BufferedReader(new InputStreamReader(System.in, keyboardCharset(System.out.charset()))),
                        System.out,
                        jobs(System.out),
                        RoundRun.SESSION_ASKS);
                return;
            }
            if (rest.size() != 3) {
                System.out.print(RoundRun.usage());
                code = 2;
                return;
            }
            System.out.print(RoundRun.once(rest.get(0), rest.get(1), rest.get(2), jobs(System.out)));
        } catch (IllegalStateException ex) {
            System.out.println(ex.getMessage());
            code = 1;
        }
    }

    /** 0 是跑完，1 是没问成，2 是用法不对。 */
    @Override
    public int getExitCode() {
        return code;
    }
}
