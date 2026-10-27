# Mini Program Routing (WeChat)

You are here because the request is about creating, modifying, debugging, compiling, previewing
in a simulator or publishing a WeChat mini program, or adding a cloud-service capability to it.
Two paths can serve it. Establish what is already on disk,
then pick the path by which capabilities the request actually needs.

Mini-program development, publishing and audit submission are all live. Nothing in this file is a
preview or a partial rollout, so never describe these product capabilities as unavailable, coming
later, or requiring manual upload in WeChat DevTools. This does not imply that every validation
tool or runtime is available in the current session; report their actual coverage under C4.

## Generated project baseline — applies to both paths

### Project structure

A wrong layout does not error — it renders a blank screen. Generate exactly this shape:

```
my-miniprogram/
├── project.config.json      # { "miniprogramRoot": "./", "compileType": "miniprogram", "appid": "..." }
├── app.json                 # REQUIRED — pages / window / lazyCodeLoading
├── app.js                   # REQUIRED — App({})
├── app.wxss
├── sitemap.json             # { "rules": [{ "action": "allow", "page": "*" }] }
├── pages/
│   └── index/               # per page: .wxml + .js required, .json + .wxss optional
│       ├── index.wxml
│       ├── index.js
│       ├── index.json       # { "usingComponents": { "my-bar": "/components/my-bar/my-bar" } }
│       └── index.wxss
├── components/
│   └── my-bar/              # same 4 files; .json MUST have "component": true
└── utils/
```

- **Keep `app.json` in the same directory as `project.config.json`** (`miniprogramRoot: "./"`). Only
  keep a nested source root (e.g. `miniprogram/`) when continuing a project that already has one —
  then every path below resolves against *that* directory instead.
- **`pages` must list every page**, as source-root-relative paths with no extension and no leading
  `/`: `"pages/index/index"`. A page directory missing from `pages` is not compiled. First entry is
  the launch page.
- Use `Page({})` for pages, `Component({})` only for components.
- **Default to plain `.js` + `.wxss`.** `.ts` / `.scss` are silently ignored unless
  `project.config.json` has `setting.useCompilerPlugins: ["typescript", "sass"]`.
- `tabBar` needs 2–5 entries, each `pagePath` also in `pages`, and each `iconPath` an image file that
  actually exists — omit `iconPath` rather than referencing one you did not create.
- All these JSON files are strict JSON: no comments, no trailing commas.
- **Never** create `index.html`, `src/`, `.vue` / `.jsx` / `.css`, or any bundler config
  (`vite.config.*`, `webpack.config.*`). In `.wxml` use `<view>` / `<text>` / `<image>`, never
  `<div>` / `<span>` / `<img>`; in `.js` there is no `document`, `window`, `localStorage` or `fetch`
  — use `wx.*`.
- npm deps (incl. the cloud SDK) need `package.json` next to `app.json`, then 「构建 npm」.
- Put Node test/smoke scripts in `tests/` under the actual mini-program source root. Merge
  `{ "type": "folder", "value": "tests" }` into `project.config.json` → `packOptions.ignore`,
  preserving existing rules and ensuring `packOptions.include` does not re-include those scripts.

Before handing over, read the files back: `miniprogramRoot` points at the directory holding
`app.json`, and every `pages` entry has a real `.wxml` + `.js`.

- WXML expressions are not JavaScript: do not use `?.`, `??`, arrow functions or method calls
  inside `{{ }}`. Compute complex values in page/component JS and expose the result through data.
- Check every WXML `include` / `import`, external WXS `src`, WXSS `@import` and local
  `usingComponents` reference against saved files, including filename case and relative paths.
- When a compile check reports an error, fix the named file and repeat the corresponding check.
  A successful JS syntax check does not verify WXML, WXS or WXSS, and an H5 preview does not
  establish native compilation success. Report unavailable checks as unverified; never publish
  or upload solely to obtain a validation result without a user request to publish.

### Component lazy loading

Every native WeChat mini program you generate must enable component lazy loading in the
**top-level `app.json` object**:

```json
{
  "lazyCodeLoading": "requiredComponents"
}
```

This is a field to merge into the application's configuration, not a replacement for the whole
file. Put it in the actual mini-program source root (`project.config.json`'s `miniprogramRoot`
when configured), not in `project.config.json`, a page JSON, or a DevTools-only setting.

Include it from the first generation. When continuing an existing project under Step 1, preserve
the other settings and add this field if missing. A project without it has reproduced an iPhone
white screen with `__wxAppCode__` undefined during component injection, despite rendering in the
DevTools simulator; explicitly setting it resolved that case.

Before handing over the project or proceeding to preview or publishing, **read and parse the
saved `app.json` and verify `lazyCodeLoading === "requiredComponents"`**. Correct a missing or
incorrect value before continuing. This is a source-configuration check and is required even on
the Cloud-service path, where C4 limits verification claims to checks actually performed. Passing
this check or seeing a working simulator is not evidence that the phone runtime has been verified.

## Step 1 — Inspect the directory before deciding anything

This step does not pick a path. It establishes one fact that constrains both: whether there is
existing work you could destroy.

Inspect the project directory referenced by the user; use the working directory when no project
was referenced. The referenced project may be outside the session's working directory. Treat it
as an existing mini-program project when **any** of these holds, checking the directory itself
**and one level of subdirectories**:

- `project.config.json` or `project.private.config.json` whose content mentions
  `miniprogramRoot` / `compileType` / `appid`
- `app.wxss` / `app.acss` / `app.ttss` exists
- `app.json` contains a `pages` array

Subdirectories must be checked too: a project written into `todolist-miniprogram/` with an
unrelated `index.html` at the root is a real, observed layout.

**If an existing project is found, do not treat the request as a from-scratch build.** Someone
saying 「帮我做个小程序」 inside a directory that already holds one is far more likely continuing
that work than asking you to flatten it.

Before you change anything in it, **confirm with the user**, and put the choice to them
explicitly:

- **Continue the existing project** → never regenerate or overwrite its files; work incrementally
  on top of what is there, then choose the path by Step 2.
- **Start a separate new one** → they must name a new, empty directory for it, and the work happens
  *there*, never on top of the existing project.

Never rewrite files in an existing mini-program project without that confirmation. That
prohibition holds on both paths — it is about not destroying work, not about which path you pick.

Then continue to Step 2.

## Step 2 — Route by intent signal

A row matches when **any one** of its signals is present; the signals within a row are
alternatives, not joint requirements.

| Intent signals | Path |
|---|---|
| **Continue developing an existing WeChat mini-program project** (add pages, change features, fix defects) · explicitly requests **WeChat DevTools** · needs **real-device debugging**, **compilation**, **simulator preview**, **automated tests** or a **performance profile** | **Professional mini-program development**（微信小程序专业开发）— follow `wechatide-skill` as a reference |
| Wants it **published** / **released** · wants a **QR code to hand out** · states a capability need (**data storage / login / upload / AI / multi-user interaction**) | **Cloud-service path** — stay in the cloud-service chain |

Read the rows as written. Do not weigh the two paths' capabilities against each other, do not
reason about which one is a better fit, and do not invent signals that are not listed — matching a
signal is the whole decision.

**When signals from both rows match, choose Professional development.** 「做一个健身打卡小程序，
需要把数据存储在云数据中，需要真机调试」 matches both rows, and it is a **Professional
development** request. One professional-development signal is enough to decide it — the
Cloud-service signals alongside it do not dilute that, do not earn a first half, and do not make it
a two-phase job.

Once Professional development is chosen, **it is the only path you run.** Do not do any
Cloud-service work first, do not `inspect` or `activate` the cloud service, do not create an
application, do not integrate the SDK, and do not publish anything — not before, not after, not as
preparation. The user's storage / login / upload / AI need goes to `wechatide-skill` along with
everything else; announce it (see the Professional development section) instead of serving it here.

There is no "Cloud-service first, then professional development" sequence in this file. If you catch
yourself planning one, you have mis-read this step: re-read the rows and take the
professional-development path.

「在这个项目上加几个页面」 with a referenced existing WeChat project is a professional-development
request even without a new backend or publishing need. A follow-up 「我想真机调试看一下效果」,
「编译一下」 or 「在模拟器里预览」 also takes this path; load the reference before attempting
development commands or replacing the workflow with manual instructions. The mere presence of
project files does not imply continued development: a request only to publish an existing project
still takes the Cloud-service path. Negated or quoted DevTools mentions are not positive signals;
「不用开发者工具，直接发布」 remains a publishing request.

## Step 3 — When neither row matches

Some requests carry no signal from either row — 「帮我做个微信小程序，页面要好看一点」 describes a
visual preference, not a development tool need or a backend capability.

A mini program is architecturally different from a web page — WXML/WXSS/JS vs. HTML/CSS/JS, a
different packaging model, and a different distribution channel. When the user said "小程序" but
named no cloud-service capability or publishing intent, **do not silently produce a web page**.
Instead, **ask the user to clarify**: e.g. 「你想做微信小程序还是网页应用？如果是微信小程序，我
需要帮你开通云服务来支持数据存储和发布。」 If the user confirms they want a mini program, proceed
on the **Cloud-service path**. If they say a web page is fine, leave routing.md and produce a web
product.

Asking about path attribution is also fine when it would genuinely change the plan — e.g. 「你是想
直接发布出去拿到二维码，还是需要在开发者工具里真机调试？」 That is consistent with the rule in
`SKILL.md`: asking WHICH path is allowed, asking WHETHER to enable cloud service is not. Path
attribution is the former.

What you must not do is stall. Ask, or pick the cloud-service path — never leave the request
unrouted.

## Professional mini-program development（微信小程序专业开发）— follow `wechatide-skill` as a reference

`wechatide-skill` owns the entire WeChat DevTools workflow: install diagnosis, downloading and
updating DevTools, the readiness gate, and every development scene (compile / preview / upload /
automation / debug). Your job here is **only** to fetch that material and then follow it as
reference documentation.

**It is reference material, not a skill you install.** Fetch it, read it, do what it says — that
is the whole mechanism. In particular:

- **Never register it as a skill.** Do not load it through the Skill tool and do not do anything
  else that makes the host treat it as an installed skill.
- **Never write it into the skills directory.** `$WORKBUDDY_CONFIG_DIR/skills/` (default
  `~/.workbuddy/skills/`) is scanned by the host, which registers anything with a `SKILL.md`
  frontmatter. Unpacking there installs it as a side effect, which is exactly what is forbidden.
  This applies to every path under that directory, and to any other location the host scans.
- **Its own scripts stay runnable.** The scenes it documents work by running the scripts it ships,
  so executing those from the reference directory is expected and allowed — that is following the
  material, not installing it. What is forbidden is registration, not execution.

**Do NOT restate anything it already owns** — not its script names, not its CLI flags, not its
scene procedures. Those evolve with its version; a copy here goes stale and makes you follow it
wrongly.

Note the distinction: `wechatide-skill`'s own installer scene downloads **WeChat DevTools** — that
is its job and it stays its job. The steps below download **the `wechatide-skill` material
itself**, which it cannot do for itself.

**Do not `inspect` or `activate` the WorkBuddy cloud service on this path.** The rule in
`SKILL.md` — activate as soon as a backend is needed — does not apply once you have routed to
professional development: activation provisions a real resource that may carry quota or billing, and
this path builds against WeChat DevTools rather than against `publicConfig`. This holds even when the
request also named a backend need; that need belongs to this path too.

So **name the need and leave it with `wechatide-skill`**: say what the user asked for (login,
storage, upload, …) and that the DevTools workflow now owns how it gets built. Do not let it
disappear silently, and do not fill it yourself — not with the WorkBuddy cloud service, not with any
third-party backend.

Mentioning that WorkBuddy can also host and publish the mini program is fine **as a closing note**,
if the user later wants that instead. It is not an invitation to start doing it now: stating it must
not turn into activating anything, and the current request stays on this path.

When the user's request includes a backend need alongside a professional-development need, **state
clearly** that the backend capability (storage, login, etc.) is available through the WorkBuddy
cloud-service path and can be set up as a follow-up step after the DevTools development workflow.
This gives the user an actionable path for both needs — not a dead end.

### H1 — Skip the download if the reference is already there

Check whether `$WORKBUDDY_CONFIG_DIR/references/wechatide-skill/` exists (default
`~/.workbuddy/references/wechatide-skill/`).

- **Exists** → skip H2 entirely, go straight to H3.
- **Missing** → continue with H2.

Do **not** compare versions here, and do not re-download to "refresh" a copy that is already
present. If what you read there turns out to contradict what the user's DevTools actually does,
delete the directory and run H2 once — that is the only reason to fetch it again.

### H2 — First-time fetch

Fetch the version manifest from exactly this URL. Copy it verbatim — never edit the host or path:

```
https://devtools.wxqcloud.qq.com.cn/WechatWebDev/skills/wechatide-skill/version.json
```

If that fetch itself fails — network error, a non-200 response, unparseable JSON, or a body with
no `downloadUrl` — **stop and tell the user.** The same prohibitions apply: do not retry in a
loop, do not switch to another source, and do not guess a download URL.

The `downloadUrl` in the response is the only dynamic value. Validate all three before
downloading it:

1. starts with `https://`
2. its prefix is `https://devtools.wxqcloud.qq.com.cn/WechatWebDev/skills/`
3. its filename matches `wechatide-skill-<semver>.zip`

If any check fails: **stop and tell the user.** Do not retry, do not switch source, do not
hand-assemble a URL.

Before unpacking, list the archive entries (`unzip -l`) and **stop** if any entry contains `../`,
an absolute path, or is a symbolic link. A symlink pointing outside the target directory escapes
it just as effectively as `../`, and a plain path check will not catch that.

Only after that check passes, unpack into `$WORKBUDDY_CONFIG_DIR/references/wechatide-skill/`
(default `~/.workbuddy/references/wechatide-skill/`). Unpack **only** there. The host does not
scan that directory, which is the point: the material lands somewhere readable without becoming an
installed skill. Never unpack into `$WORKBUDDY_CONFIG_DIR/skills/`, never copy or symlink it there
afterwards, and never move it there later "so it loads properly" — being loadable is the outcome
this path rejects.

Then confirm `$WORKBUDDY_CONFIG_DIR/references/wechatide-skill/SKILL.md` exists. If the unpack
failed or left that file missing, **remove the directory you created** and tell the user — leaving
a half-written directory behind would make H1 skip the fetch forever while the material stays
unreadable.

Then continue with H3.

### H3 — Follow it

**Read** `$WORKBUDDY_CONFIG_DIR/references/wechatide-skill/SKILL.md` as a file and follow its
instructions from there. Do **not** load it through the Skill tool: that registers it as an
installed skill, which this path does not do. Reading the file gives you the same content without
that side effect.

Read **only the root file** first. Its sub-scenes — `installer`, `initializer`, `compiler`,
`debugger`, `automator`, `previewer`, `project-manager`, `project-config` — are referenced by the
root file's own routing table by relative path; resolve those paths against the same reference
directory and read the one scene it routes you to. Do not read them speculatively, and do not mix
atomic tools across scenes: it requires entering exactly one scene per primary goal.

Its routing table also lists a **`cloudbase-operator`** scene. **Do not enter it.** A backend need
belongs on the Cloud-service path, which is where WorkBuddy provides storage, login, upload and
model calls; routing a user into a third-party backend from here gives away the very capability
this chain owns. If the need surfaces mid-scene, name it and point at the Cloud-service path
instead.

Follow the scene as written, **including running the scripts it tells you to run** — resolve those
paths against the reference directory too. Its install diagnosis, its guidance for installing
DevTools, its readiness gate and its scene routing all work that way. Only the registration step
is dropped on this path; everything the material tells you to execute, you execute.

**Once you are on this path, stay on it.** Do not return to the cloud-service flow.

## Cloud-service path — end-to-end in this chain

Stay in the cloud-service chain described by `SKILL.md`. Five mini-program-specific points.

### C1 — The environment lifecycle is unchanged

Run `inspect` → pick the target application (`reuse` / `create`) → `activate`, exactly as
`SKILL.md` describes, with one addition: when creating the application, **pass
`appType: "miniprogram"`**.

That flag defaults to `"web"`, is written once at creation, and `reuse` never rewrites it. Omit it
and the app is permanently recorded as a web app — the app space will keep offering it a preview
URL and a re-publish button, neither of which means anything for a mini program, and there is no
second chance to correct it. Decide it from what the user asked for; do not look for
`project.config.json` to confirm, since on a from-scratch build nothing has been written yet.

Every other existing constraint carries over without exception:

- Activation confirmation is raised by the trusted WorkBuddy UI. **Never** ask 「要不要开通云服务」
  yourself, and never gate activation with `AskUserQuestion`.
- The tool rejects `confirmed` / `confirm` / `skipConfirm` / `force`.
- `declinedByUser: true` is a normal outcome, not an error.
- No silent downgrade to `localStorage` / an in-memory array / a mock JSON file, unless the result
  carries `useLocalImplementation: true`.

### C2 — SDK integration uses the mini-program contract

The two web forms do not apply to a mini program:

- the root npm package (`createWorkBuddyCloud`) targets a browser runtime;
- the CDN IIFE needs a `<script>` tag, which mini programs do not have.

The security model also differs: `publishableKey` relies on the server matching an exact
**Origin**, and a mini program has no Origin — it uses `wx.request` against a request-domain
allowlist and sends `Referer: https://servicewechat.com/{appid}/...`. The mini-program SDK
handles this itself.

**That allowlist is why every mini program shares one endpoint.** WeChat does not accept
wildcards in the request-domain allowlist and caps how many domains it holds, so a per-application
domain could not scale: each new app would need a manual WeChat console change. `publicConfig.endpoint`
for a mini-program application is therefore the environment's fixed gateway —
`mp-api.app-staging.workbuddy.host` (staging) / `mp-api.app.workbuddy.host` (production) — **not**
the application's own domain. Web applications still get their own domain; the two app types
diverge here on purpose.

The same host is registered idempotently in WeChat's request / upload / download allowlist by the
publish flow before the code is uploaded, so a release needs no manual domain setup.

**Never probe that gateway directly, and never judge it by what a direct request returns.** It only
forwards requests that carry the mini program's credentials — the `publishableKey` the SDK injects
plus WeChat's `Referer: https://servicewechat.com/{appid}/...`. A bare `curl`, a browser visit or a
`wx.request` you hand-wrote has neither, so the gateway does not route it to the cloud API at all; it
falls through to a generic HTML page such as 「链接已失效」/「该应用尚未发布」. **That page is the
expected response to an unsigned request. It is not evidence that the host is wrong, that it is a
web-publishing/share domain, that it "provides no REST service", or that `publicConfig.endpoint`
needs correcting.** Concluding any of that from a `curl` and then rewriting `endpoint` is a real
observed failure: it replaces a correct value with a broken one while the actual bug stays unfixed.

So when data does not show up, debug through the mini program itself, not against the host:

- Diagnose from the running client's logs — [`diagnostics.md`](diagnostics.md) prints the failing
  method, URL, HTTP status and error code from inside the signed request path.
- To exercise the data plane outside the mini program, use the environment's own management surface
  (the cloud-service tools / app space), never a raw request to the gateway.

Two things to keep straight when debugging a rejected request:

- A domain rejection (`url not in domain list`) means the fixed host is missing from WeChat's
  allowlist, is not ICP-filed, or the artifact predates the fixed-gateway config. It is **not**
  something to fix by editing `publicConfig.endpoint` — that value comes from the backend and
  changing it locally only breaks the safety property described below.
- Do **not** hardcode any hostname, and do **not** "correct" `publicConfig.endpoint` to some other
  domain. The SDK gets it from `publicConfig.endpoint` at runtime; the application's own domain is
  never the right value for a mini program, and the fixed gateway is not interchangeable with it.

`wx.connectSocket` is out of scope: the fixed gateway serves HTTPS only and no socket domain is
registered, so do not add WSS-based features.

Integrate through the dedicated subpath
`@tencent-ai/workbuddy-cloud-sdk/miniprogram`, and only the factory that subpath exports:

```ts
import { createMiniProgramWorkBuddyCloud } from '@tencent-ai/workbuddy-cloud-sdk/miniprogram'

export const cloud = createMiniProgramWorkBuddyCloud({
  endpoint: publicConfig.endpoint,
  publishableKey: publicConfig.publishableKey,
})
```

- Install from the `dev` channel (`@tencent-ai/workbuddy-cloud-sdk@dev`), same as Form A in
  `SKILL.md`. Do not pin a version and do not use `latest`.
- Pass **both** `publicConfig` values. A mini program has no `location.origin`, so the SDK's
  same-origin fallback does not exist here — omitting `endpoint` fails at initialization.
- The factory returns the same client shape as the web form (`cloud.auth` / `cloud.database` /
  `cloud.storage` / `cloud.llm`); the module docs apply unchanged. Internally it wires the
  mini-program polyfills, the `wx.request`-backed fetch and the `wx` storage in one fixed order —
  that ordering is the SDK's job, not the app's.
- Do **not** import `createWorkBuddyCloud` from the root entry and hand-assemble
  `createMiniProgramFetch` / `createMiniProgramStorage` / `ensureMiniProgramPolyfills` yourself:
  skipping or reordering any step yields a client that constructs fine and fails on the first
  request.
- Do **not** call `wx.request` directly against `/.cloud/**` or any data-plane path — that bypasses
  `publishableKey` injection and session management.
- The CDN `<script>` form is **not** available to mini programs — there is no HTML entry to host
  the IIFE global. So the install-failure fallback in `SKILL.md` does **not** switch forms here:
  fix the npm install instead (network / registry), never fall back to hand-written `wx.request`
  wrappers.

#### Virtual-payment requests on the Cloud-service path

Before generating any `wechatVirtual*` code, call `workbuddy_miniprogram_inspect_virtual_payment`
first, then follow [`virtual-payment.md`](virtual-payment.md).

- Only when inspect says binding is `existing + authorized`, scope `157` is present,
  `miniProgram.principalCategory` is one of
  `individual | enterprise | institution | sole_proprietor`, payment credentials are configured,
  and each requested resource domain is `enabled` in `capability.domains` may you generate runnable
  payment SDK calls.
- If binding fails or scope 157 is missing, tell the user to use the existing mini-program
  rebind/reauthorization flow, and do **not** generate runnable xpay code.
- `openConditions` is a **guidance checklist, not a gate**: when any of `serviceCategory` /
  `verification` / `icpFiling` is not `met` (including `unknown`), or `openConditions` is absent, you
  may still generate the code but **must** tell the user which prerequisite is outstanding and what
  to do next (cite its `reason` / `action`). `unknown` must not be presented as satisfied.
- If `miniProgram.principalCategory == ""` or capability returns
  `principal_category_unavailable`, tell creator to verify category in MP backend
  **支付与交易→虚拟支付** first, then rebind/reauthorize; do **not** generate runnable payment code.
- If a requested resource domain is not enabled, refuse that domain with actionable guidance by citing
  `domain` / `status` / `action` from `capability.domains`, and do **not** generate bypass code.
- For official capability links: use personal link for `individual`
  (`https://developers.weixin.qq.com/miniprogram/dev/platform-capabilities/business-capabilities/virtual-payment/person.html`),
  and enterprise link for `enterprise | institution | sole_proprietor`
  (`https://developers.weixin.qq.com/miniprogram/dev/platform-capabilities/business-capabilities/virtual-payment.html`).
- If payment credentials are not configured, stop at prerequisite guidance; do not generate code that
  pretends to run without credentials.
- After generating payment code, **recommend** (do not force) that the app persist its own
  purchase record in its own cloud database for idempotent delivery; model entitlement only if its
  billing model needs it, and do **not** create a separate delivery-result table (WeChat maintains
  delivery state). The platform stores no business data. See
  [`virtual-payment.md`](virtual-payment.md) section 6.

### C3 — The destination is the mini-program artifact card

Not a sites HTTP link.

- **Do NOT call `workbuddy_sites_deploy`.** A mini-program directory is hard-rejected by the
  deploy pre-check and comes back as `sites_deploy_unsupported`.
- **Do NOT turn the mini program into a web page to get something published.** Writing an H5
  version and deploying that, or deploying a sibling `index.html`, makes the request *look*
  fulfilled while the user has no mini program. The existing deploy guidance already forbids this.

Publishing is live and driven entirely by the UI. The user reaches it from the **mini-program
artifact card in the conversation**, and that card is the only entry you name:
**点击产物卡片 → 打开预览 → 预览面板右上角「分享 / Share」→ 发布**.

**Never write 「应用空间」 to the user.** It is an internal name for that surface; saying it sends
them hunting for a menu they cannot find. Describe the click path above instead.

Two publish types are offered there — 试用小程序 and 绑定已有小程序; the closing block below is the
wording to use for both.

Publishing packages the local project, uploads it, submits it for review and releases it, and ends
with a scannable mini-program QR code. Do not try to drive any of that yourself: do not build a
ZIP, do not call the publish API, and do not open DevTools to upload.

**Your deliverable**: the mini-program project on disk. Register it per **Delivering artifacts** in
`SKILL.md`; a mini program has no entry HTML, which is the only mini-program-specific fact that rule
needs.

Ongoing management — re-publishing and cloud-service configuration — lives at
**设置 → 数据管理 → 应用**. Name it too, since the artifact card belongs to one conversation.

#### Hold these back until they are actually needed

All true, but listing them at delivery turns a finished product into a wall of caveats. Bring one up
when the user asks about 正式版, hits the corresponding prompt, or plans long-term operation:

- 正式版需要小程序在微信公众平台完成备案，并填写隐私协议。首次发布正式版时会弹出隐私表单（联系
  方式 + 收集了哪些数据）；C5 的清单已经列好了「收集了哪些数据」那一半，自己读出来告诉用户即可，
  不要让用户去打开那个文件。
- 试用小程序只能发布试用版，无法升级为体验版或正式版 —— 打算长期运营的用户从一开始就绑定自己的
  小程序账号。
- 发布正式版会同时上线一个体验版；正式版审核占用每周有限的提审额度。

#### Close every delivery with 「如何预览和发布」

The report's **last** section is this block, as one continuous structured statement, so the user
finishes reading knowing exactly what they can do next. State the verification outcome required by
C4 before this block; keep this block focused on what to click. Nothing goes after it: no duplicate
verification note, no static-check inventory, no privacy caveat (C4 and C5).

Adapt the wording, keep the structure and the order:

```markdown
## 如何预览和发布

**预览**：点击下方的小程序产物卡片即可打开预览。

**发布**：在预览面板右上角点击「分享 / Share」，可以发布两种小程序：

- **试用小程序**：扫码一次即可，无需提前准备小程序账号，有效期 14 天。适合先体验成果或临时分享。
- **绑定已有小程序**：扫码一次授权你的小程序账号，之后平台会推送代码并提交审核。可以先发布体验版
  查看效果，再提交正式版走微信审核。

**后续管理**：在「设置 → 数据管理 → 应用」中可以管理小程序的发布和云服务。
```

### C4 — Use platform validation and report its actual scope

Do **not** install DevTools or fetch/read the `wechatide-skill` reference on this path. Use the
platform's available validation instead; a validation Hook does not change the route chosen in
Step 2. Never publish or upload solely to obtain a validation result.

After `Write` / `Edit`, when a platform Hook returns diagnostics, use its original file, line,
column and reason to fix the reported problem, then repeat the same check on the saved change.
Do not replace a compiler error with a guess or infer success from a silent Hook. If the compiler
is unavailable or a check is not covered, mark that check as **not performed**, not passed.

During generation, a referenced file may still be waiting to be written. Complete the planned
files before treating that reference as a final missing dependency; recheck references against the
saved project before delivery. A passing single-file check does not establish that the complete
project compiles. JS syntax success does not cover WXML/WXS/WXSS, and H5 preview success does not
establish native compilation or real-device success.

Apply the **Completion Bar** in `SKILL.md` according to the evidence available:

- **Item 2**: integrate each module per its `code-generation.md`, including the mini-program
  factory and both `publicConfig` values in C2. Claim the code runs only in an environment where
  execution was observed; compilation alone does not establish runtime behavior.
- **Item 3**: claim a critical path verified only after observing it, such as a write followed by
  a read or login returning an identity. A Hook compilation result does not verify these flows.
- **Item 4**: implement the documented errors for network failure, not-logged-in, insufficient
  permission and out-of-quota. Distinguish implemented handling from behavior actually exercised.
- **Items 1, 5 and 6** remain required: provision the environment through C1, report what was
  activated, and derive the privacy manifest from the final code under C5.

Before delivery, resolve reported source errors and repeat the corresponding checks. Briefly state
what validation passed, which checks were not performed, and any unresolved error with its concrete
reason. Do not claim automatic full acceptance, a working critical path or real-device validation
without that evidence. Keep this outcome concise; do not dump the internal checklist.

Publishing remains available through C3. A check that was not performed is not evidence that the
publishing entry is unavailable, and is not a reason to tell the user to upload from DevTools.

### C5 — Write the privacy manifest last, always

Once the code is final, you **MUST** write `.<applicationId>.privacy.json` — a hidden file, leading
dot included, e.g. `.wbapp_a1b2c3.privacy.json` — at the mini-program project root: the enumeration
of every privacy capability the generated code exercises, as `[{ "<privacy_key>": "<privacy_text>" }]`.

This is **not optional and not conditional** on the user asking for it, and it genuinely comes
last — the manifest describes finished code, so deriving it any earlier would describe code you
had not written yet. It applies even when the app collects nothing: then the file is `[]`, which
states that positively instead of leaving it unknown.

Read [`privacy-manifest.md`](privacy-manifest.md) for the official `privacy_key` values, the
`wx.*`-to-key mapping, the self-collected `EX*` keys that no grep will surface, and how to word
`privacy_text`. Do **not** write the file from memory — the key list is long, and a key that is
merely plausible fails review.

The id in the filename is the **`applicationId`** (`wbapp_…`) from C1, **not** the WeChat appid —
nothing is bound at this point, so no WeChat appid exists yet (C3). The file itself **configures
nothing**, and it is written silently: `privacy-manifest.md` §Do not report it has the reporting
rules.
