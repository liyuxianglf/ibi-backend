### 一、项目介绍
锐智能BI平台，基于 React + Spring Boot + MQ + AIGC 的智能数据分析平台，区别于传统 BI，用户只需要导入原始数据集、并输入分析诉求，就能自动生成可视化图表及分析结论，实现数据分析的降本增效。
### 二、在线体验
在线访问：http://182.254.135.166:443/ 可以自行注册账号
### 三、项目背景
在传统BI平台中需要专业人士人工筛选原始数据然后才能生成对应的图表。而在锐智能BI平台中，用户只需要导入最原始的数据并输入想要分析的目标（比如分析一下网站最近一年用户的增长趋势）就能利用AI自动生成一个符合要求的图表以及结论。
### 四、核心功能
1. 智能数据分析：用户通过输入分析目标和导入原始数据便可以生成分析结论和数据图表。
2. 我的图表：用户可以查看自己之前生成的图表，支持按图表名称模糊搜索。
### 五、智能分析业务功能实现流程图
![图片](https://github.com/liyuxianglf/ibi-backend/assets/151162920/127ac50f-0eb3-44e9-8c33-de16f3caace4)
### 六、项目亮点
1. (业务流程)后端自定义 Prompt 预设模板并封装用户输入的数据和分析诉求，通过对接 AIGC 接口生成可视化图表 json 配置和分析结论，返回给前端渲染。
2. 由于AIGC 的输入 Token 限制，使用 Easy Excel 解析用户上传的 XLSX 表格数据文件并压缩为 CSV，实测提高了 20%的单次输入数据量、并节约了成本。
3. 为防止某用户恶意占用系统资源，使用基于 Redisson 的 RateLimiter 实现分布式限流，控制单用户访问的频率。
4. 由于 AIGC 的响应时间较长，基于自定义 IO 密集型线程池+任务队列实现了 对接AIGC 接口的并发执行和异步化，提交任务后即可响应前端，提高用户体验。
5. 由于本地任务队列重启丢失数据，使用 RabbitMQ (分布式消息队列)来接受并持久化任务消息，通过 Direct 交换机转发给解耦的 AI 生成模块消费并处理任务，提高了系统的可靠性。
### 七、技术选型
后端
- SpringBoot + Mybatis + MyBatis-Plus

后端组件
- Redisson + 鱼聪明 AI SDK + EasyExcel + Knife4j + Hutool、Apache Common Utils等工具库 

中间件
- MySQL + Redis + RabbitMQ + Nginx
### 八、后端部署
1. 执行安装后端所需依赖： maven install
2. 使用根路径下的/sql/create_table.sql创建数据库和对应的表。
3. 修改application-open.xml文件，将Mysql、Redis、RabbitMQ的地址改为自己的地址，并将AI接口的秘钥改为自己的。
### 九、界面展示
![1702630076091](https://github.com/liyuxianglf/ibi-backend/assets/151162920/2653d3ff-4322-4b59-9564-c239aeab749f)

图表对应的xlsx文件示例
![1702630107750](https://github.com/liyuxianglf/ibi-backend/assets/151162920/da3ba978-8cb6-456b-9ba0-330b092b0596)

