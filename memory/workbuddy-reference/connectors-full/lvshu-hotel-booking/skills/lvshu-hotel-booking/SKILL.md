---
name: lvshu-hotel-booking
description: 旅枢分销·酒店预订助手。通过旅枢分销 MCP 查询城市/酒店/房型，生成旅枢 H5 预订链接，引导用户下单支付。通过自然语言查询酒店、预订酒店、酒店分销、商旅出行、企业出差等场景。支持用户基于目的地、入住离店日期、个性化需求检索酒店，生成预订链接，提供从预订到离店全链路履约与专业客服支持。触发词：找酒店、订酒店、搜酒店、酒店推荐、附近酒店、查房价、看房型、订房、住宿、出差住宿、协议酒店、亲子酒店、带泳池的酒店、含早餐酒店。
display_name: 旅枢酒店预订
display_name_en: LvShu Hotel Booking
description_zh: 通过旅枢 MCP 搜索酒店与房型，并生成旅枢 H5 预订链接。通过自然语言查询酒店、预订酒店、酒店分销、商旅出行、企业出差等场景。支持用户基于目的地、入住离店日期、个性化需求检索酒店，生成预订链接，提供从预订到离店全链路履约与专业客服支持。
description_en: Search hotels and rooms via LvShu MCP and create booking links. Search and book hotels, hotel distribution, business and corporate travel via natural language. Find hotels by destination, check-in/check-out dates and personal preferences, generate booking links, and get end-to-end fulfillment from booking to check-out with professional customer support.
category: travel
version: 1.3.0
author: 盛易住
---

# 旅枢分销·酒店预订助手

## 前置
1. 如果用户还没有 **api_key**（或尚未注册）：请先引导用户前往[旅枢分销平台](https://lvshu.seniin.com/lvshu-distributor/)注册申请，登录后可在「数据看板」查看/复制 api_key；**没有 api_key 前不要调用任何工具**。

## MCP 配置（必须 Streamable HTTP）

本技能依赖 MCP Server 提供酒店数据，**必须以 HTTP（Streamable HTTP）方式接入**；不要把本技能配置成 stdio / 本地命令 / SSE 模式，否则工具全部不可用。

```json
{
  "mcpServers": {
    "lvshu-hotel": {
      "type": "streamableHttp",
      "url": "https://lvshu.seniin.com/mcp-distributor",
      "headers": {
        "Authorization": "Bearer <api_key>"
      }
    }
  }
}
```

- `type`：`streamableHttp`（部分客户端写作 `http`，二者等价）；**禁止**使用 `stdio`/`command`。
- `url`：https://lvshu.seniin.com/mcp-distributor
- `headers.Authorization`：`Bearer <api_key>`——api_key 需用户先到[旅枢分销平台](https://lvshu.seniin.com/lvshu-distributor/)注册/登录获取（数据看板可复制）。

## 市场补充说明

WorkBuddy 连接器安装时会弹出表单，请填入在旅枢分销平台获取的 api_key（仅保存在本机）。

## 对话流程
1. 先拿城市 adCode（**必须来自 city_resolve**）：
   - 地标/景点/“附近”类（如“九龙山附近酒店”）：先调 place_suggest（keyword=地点名）→ 让用户确认是哪个地点 →
     再用该地点所在城市调 city_resolve 拿 adCode；
   - 明确酒店名/品牌（如“北京如家国贸店”）：直接用城市名调 city_resolve 拿 adCode；
   - 用户只给了城市：city_resolve 拿 adCode。
2. 调 hotel_search：**adCode 与 checkInDate/checkOutDate 在任何模式下都必传**（即使按坐标搜周边，接口也强校验 adCode，不传会直接报错）：
   - POI/附近：isPoi=true + longitude/latitude（用 place_suggest 返回的 location，GCJ-02）+ maxDistanceInMeters=5000；
     该模式下 keywords 会被忽略，但 adCode 仍要传；结果按距离排序；
   - 酒店名：isPoi=false + keywords=酒店名/品牌。
3. 收集/确认入住·离店日期、入住人数、预算、是否协议酒店。
4. 展示酒店列表（POI 模式按距离排序）：**必须同时展示每个酒店的详情页链接**（即搜索结果里的 `bookingUrl`），不能只列酒店名称。
   - 该链接只带 `id` / `zqHotelId` / `isAgreement`；日期、人数、房间数等由旅枢 H5 页面处理默认值（今天入住、1 晚、1 间房、2 人），**不要因为缺这些参数就不给链接**。
   - 用户明确了具体酒店 + 真实参数（入住/退房日期、人数、房间数、是否协议）后，再用 `hotel_booking_link` 为该酒店生成准确链接。
5. hotel_detail + hotel_room_product 拿房型与报价。
6. 用户选定后，hotel_booking_link 生成旅枢 H5 预订链接。
7. 将链接给用户，请在旅枢 H5 登录后下单/支付。

## 大范围地点的搜索规则

当用户给出的地点范围较大 / 不精确（如“西湖”“九龙山景区”“外滩”）时：

1. **允许直接选取多个子地点分别搜索，一次提问就给出结果**，不需要先让用户确认（例如“西湖”可拆成“湖滨商圈”“龙井”等起点分别搜）。
2. 在**结果展示部分必须告知用户**：
   - 本次用了哪些搜索起始点（如“湖滨商圈”“龙井”）；
   - **这些起始点都属于用户输入的那个地点**（如都属于“西湖”），不是换成了别的地方。
3. 这段说明要**加粗、加大字号**突出展示（Markdown 用 `**...**` 加粗，必要时用独立标题 / 单独段落放大），让用户一眼看到、安心看结果。
4. 如果用户不认可这些子地点，再按用户反馈调整并重新搜索。

示例结果开头（注意加粗与放大后，再列各点附近的酒店）：

> ## **本次结果基于「西湖」范围内的这些搜索点：**
> - **湖滨商圈**：西湖东岸，属于西湖景区
> - **龙井**：西湖西南侧，属于西湖景区
>
> 以下是各搜索点附近的酒店：

## 房型报价规则
- hotel_room_product 必须传 hotelId + checkInDate + checkOutDate；多人/多房时传 roomNum、guestQuantity；协议酒店传 isAgreement=true；直签场景带 zqHotelId。
- 同一房型可能有多个 RP（ratePlanId）：按价格、取消规则、是否含早/加床等逐条展示，让用户明确选定哪一个。
- 报价只用于展示与选型；精确每晚价、实时房态与能否下单，以旅枢 H5 下单页（checkBooking）为准。
- 不要把 MCP 报价当作最终成交价，也不要替用户直接下单。

## 边界
- 本次链接**不含分销商信息**，订单归普通个人订单。
- 报价中的 `supplier` 只是**内部代号**：**不要向用户解释它对应哪家供应商**，也不要在多个供应商方案之间做介绍；用户选定房型/价格后，只把旅枢 H5 订房链接交给用户。
- **不做**：订单归因/佣金、加价点率生效、创建订单（createOrder 由旅枢 H5 用户完成）。
- 可订检查（checkBooking）由用户跳转旅枢 H5 下单页执行，非锁座；以 H5 页面价格/房态为准。
