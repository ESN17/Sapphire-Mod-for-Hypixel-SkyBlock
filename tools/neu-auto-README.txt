Sapphire NEU 增量翻译工具（待审核模式）

用途
按物品内部 ID 检测 NEU master 分支的新提交，不依赖对方是否发布 Release。
检测新增物品、改名、描述改写、数值与折行变化。配方、NBT 等无关变化不翻译。
初次基准仅有去色英文时无法追溯历史纯颜色变化；以后用本工具候选库作基准时可检测配色变化。
只将显示名称、描述和相关已审术语发送到 DeepSeek，不发送 NBT、账号信息或 Minecraft 令牌。

运行环境
Python 3.10 或更新版本，仅使用标准库，无需 pip 安装依赖。
可在 Windows 或 Linux 运行；服务器若只有 Python 3.6，请使用独立的较新 Python 环境。

独立工具包用法
1. 将整个工具 ZIP 解压到一个新文件夹，保留 tools 和 baseline 目录结构。
2. 将 deepseek.example.json 复制为 deepseek.local.json，填入自己的 API Key。
   也可以在环境变量 DEEPSEEK_API_KEY 中设置密钥；配置文件中 api_key 留空。
   密钥不会写入词典 ZIP。请不要上传 deepseek.local.json。
3. 在解压目录打开终端，执行下面命令（Windows 也可以把 python 换成 py -3）。

仅检测，不调用 API：
  python tools/neu_auto_translate.py scan

检测、翻译、校验并生成待审核 ZIP：
  python tools/neu_auto_translate.py run

持续监听（默认每小时检查一次，按 Ctrl+C 停止）：
  python tools/neu_auto_translate.py watch

离线 NEU 数据测试，目录内放 items/*.json：
  python tools/neu_auto_translate.py scan --source path/to/neu-repo
  python tools/neu_auto_translate.py run --source path/to/neu-repo

指定新 ZIP 编号，例如当天已在服务器发布了其他译名库：
  python tools/neu_auto_translate.py run --version 20261031_3

项目内直接运行也使用上述命令。项目默认读取 src/main/resources/defaults；
输出到 exports/dictionaries/pending-review，缓存位于 translation-state/neu-auto。
独立工具包则读取 baseline；输出到 pending-review，缓存位于 state。
路径与限制在 tools/neu-auto-config.json 中修改，所有相对路径均相对工具包/工程根目录。
baseline 既可以是包含 translations/ 的目录，也可以是已审核的完整词典 ZIP。

待审核流程
- 生成 YYYYMMDD_编号.zip，符合现有 /translate 的完整词典格式，包含清单、哈希及许可声明。
- 同目录的 .review.txt 方便逐项阅读英文差异和候选译文；.review.json 保留完整的前后字典和 API 用量。
- 审核候选词典时请检查语义、数字单位、专名及彩色效果；结构校验不等于游戏内人工验收。
- 该 ZIP 可以交给现有客户端离线更新测试，通过后再由授权人员上传网站。
- 只有全部待处理条目成功才打包；中途失败或达到限额不会生成一个伪装成完整更新的 ZIP。
- 重新运行会复用已通过校验的缓存；同样的基准、源文本和规则不会反复生成 ZIP。
- 新描述增补匹配规则，保留旧规则以兼容旧文本；改名只写入候选库，原基准文件保持不变。
- NEU 删除的条目只列入报告，不删除现有译文。
- 如果已有 ID 的英文名改变，会额外标注客户端兼容提示：dev 0.0.6 名称匹配仍优先内置 NEU 名称，
  发布改名项前需确认客户端已适配新名称。新增 ID 和描述更新不受此限制；本工具不改动 Mod Java 代码。
- 审核并发布后，把配置中的 baseline 指向这份已审核 ZIP，作为下一轮基准。
  如需修改候选译文，请修改独立审核目录并使用现有词典工具重新生成哈希，不要手改 ZIP 而保留旧哈希。
- 不自动安装、不自动覆盖工程、不自动上传网站，也不会修改玩家正在使用的词典。

术语规则
tools/sapphire-translation-rules.json 包含已确认术语、物品名称和地名参考。
保留 DR-X655 等型号；Slayer=杀手、Gabagool=加巴古尔、Pristine=提纯、Bits=比特、
Sea Lumies=海泡菜、Mangrove Log=红树原木、Mote/Motes=裂隙尘埃、
Enigma Souls=恩尼格玛之魂、Enigma's Cloak=恩尼格玛的斗篷。
允许保留的 NPC 名称在 allowed_english 中维护；未知英语会被标为失败，需译成中文或明确登记 NPC 例外。
原文里的提示语不能覆盖翻译规则；物品元数据一律作为数据处理。

费用与恢复
默认每次处理最多 20 个尚未完成的物品、调用最多 100 次 API；每天最多 300 次 API 请求。
每日限额按北京时间重置，失败请求也计入次数；次数限制不是金额上限，请结合 DeepSeek 账户额度设置。
单次验证失败最多重试 3 次。scan 不需要 API Key；脚本不会自动在系统中安装定时任务。
反复校验不合格的单元会隔离到 rejected/，watch 不会每小时重复花钱重译同一失败单元。
检查该文件并调整术语规则后再运行，或明确执行 run --retry-rejected 重新尝试；不能在 watch 中持续强制重试。
首次运行可能发现一批历史变更。出现 incomplete 时，先看 latest-progress.json 的 failed 和 remaining。
只有 remaining 表示达到物品处理限额，继续 run 即可；failed 应按原因检查余额、联网、术语或模型输出。
请求限额跨进程保存在 request-budget.json。不要删除状态目录来绕开预算或重复收费。
API 失败时不会记录为已完成；成功单元独立缓存，下一次只补未完成部分。
定时轮询必须保持 watch 进程运行，关闭终端或电脑后不会继续执行。
ZIP 编号按当天已有候选包最大编号加一；上传前还应核对线上最大编号，必要时使用 --version。
不抓取或翻译 NEU 未提供的 NPC 对话及服务器通知，这些仍需独立语料来源。

自检
  python -m unittest discover -s tools -p test_neu_auto_translate.py -v

来源
NEU：https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO
DeepSeek：https://api-docs.deepseek.com/api/create-chat-completion/
本工具包不包含任何 API Key，不包含服务器登录资料。
