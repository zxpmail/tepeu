# ATE-Bench 复现镜像：Python 裁判 + Maven + Node(Claude Code CLI)
# 完整 Agent 跑需要传入凭证（见 docker-compose / README）。
FROM maven:3.9.9-eclipse-temurin-21

RUN apt-get update && apt-get install -y --no-install-recommends \
      python3 python3-pip git curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Node.js 20 for Claude Code CLI
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && rm -rf /var/lib/apt/lists/* \
    && npm install -g @anthropic-ai/claude-code

WORKDIR /workspace
COPY . /workspace

RUN sed -i 's/\r$//' /workspace/experiments/ate-bench/scripts/docker-entry.sh \
    && chmod +x /workspace/experiments/ate-bench/scripts/docker-entry.sh

# 默认不做 LLM 调用：只校验实验脚本与 backend 可编译
ENV ATE_MODE=smoke
ENTRYPOINT ["bash", "/workspace/experiments/ate-bench/scripts/docker-entry.sh"]
