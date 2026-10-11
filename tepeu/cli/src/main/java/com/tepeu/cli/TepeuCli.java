package com.tepeu.cli;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 命令行入口。没有网页。
 * 真正跑一轮在 RoundRun，这里只负责启动。
 */
@SpringBootApplication
public class TepeuCli {

    /** 启动后交给 CliRunner。跑完按它的退出码离开。 */
    public static void main(String[] args) {
        System.exit(SpringApplication.exit(SpringApplication.run(TepeuCli.class, args)));
    }
}
