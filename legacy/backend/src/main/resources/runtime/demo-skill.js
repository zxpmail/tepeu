// Tepeu demo 技能脚本 — 读 /sandbox/in.txt，写 /sandbox/out.txt
// 全局：fs（WorkspaceScriptFs）、input（可选字符串）
var text = '';
if (fs.exists('/sandbox/in.txt')) {
  text = fs.readText('/sandbox/in.txt');
} else {
  text = (input && input.length > 0) ? input : 'hello-from-demo';
}
var out = 'demo-echo: ' + text;
fs.writeText('/sandbox/out.txt', out);
var result = out;
