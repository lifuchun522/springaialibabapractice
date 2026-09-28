package com.example.digitalhuman.arch;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 第 16 掌：把「分层」从目录约定变成**可执行的依赖方向**。
 *
 * <p>原理只有一句：分层的第一价值是变更隔离，而隔离的判据是依赖方向。
 * 只建目录、不管方向，`web` 里照样能 import {@code ChatClient}、import 配置类、直接查库——
 * 文件变多了，耦合一点没少。所以这一掌不重命名包，而是把方向写成规则：
 * <b>违反一次，CI 红一次</b>。
 *
 * <p>本仓库的七层映射（与文章的叫法对照，不照搬命名、但守住方向）：
 * <pre>
 * 文章        本仓库
 * api      →  com.example.digitalhuman.web          （只认 HTTP：路径/方法/媒体类型/DTO）
 * application → com.example.digitalhuman.service    （只认用例）
 * agent    →  com.example.digitalhuman.agent / workflow / multiagent / graph
 * tool     →  com.example.digitalhuman.tools
 * rag      →  com.example.digitalhuman.rag / embedding / service.ProjectKnowledgeRetriever
 * graph    →  com.example.digitalhuman.graph
 * infra    →  com.example.digitalhuman.config / repository / actuator（Spring 自带）
 * </pre>
 * 为什么保留本仓库的命名而不是改成文章的七层包名：改包名是**大范围重命名**，
 * 会让前面十几掌的验收记录、Wiki 与 tag 的路径全部对不上——收益是「名字好看」，
 * 代价是所有历史证据失联。方向守住了，名字可以不同。
 */
class LayeredArchitectureTest {

    private static JavaClasses mainClasses;

    @BeforeAll
    static void importProductionClasses() {
        mainClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.example.digitalhuman");
    }

    @Test
    @DisplayName("api 层不认识模型：web 不得依赖 Spring AI")
    void apiMustNotKnowTheModel() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.example.digitalhuman.web..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.ai..",
                        "com.alibaba.cloud.ai..")
                .because("Controller 只认 HTTP 语义与 DTO；一旦它认识 ChatClient，换模型就要改 Controller");

        rule.check(mainClasses);
    }

    @Test
    @DisplayName("api 层不认识数据：web 不得直接依赖 repository")
    void apiMustNotTouchPersistence() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.example.digitalhuman.web..")
                .should().dependOnClassesThat().resideInAPackage("..repository..")
                .because("Controller 直接查库，等于把事务边界、权限过滤和分页都搬到 HTTP 层");

        rule.check(mainClasses);
    }

    @Test
    @DisplayName("依赖方向单向：业务层不得反向依赖 web")
    void businessMustNotDependOnWeb() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(
                        "..service..", "..agent..", "..tools..", "..rag..", "..graph..",
                        "..multiagent..", "..workflow..", "..a2a..", "..embedding..", "..domain..")
                .should().dependOnClassesThat().resideInAPackage("com.example.digitalhuman.web..")
                .because("反向依赖一出现，Web 层就成了所有人编译期都躲不开的公共依赖，分层名存实亡");

        rule.check(mainClasses);
    }

    @Test
    @DisplayName("适配层特权收口：除 ai 包外，任何地方不得出现 provider 专有类型")
    void providerTypesStayInTheAdapter() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackages("..ai..", "..config..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.ai.openai..",
                        "com.alibaba.cloud.ai.dashscope..")
                .because("provider 专有类型扩散 = 换一家模型要改一片代码；本仓库只允许 ChatOptionsFactory 认识它们");

        rule.check(mainClasses);
    }

    @Test
    @DisplayName("能力层不认识 HTTP：tools/rag/graph 不得依赖 servlet 与 web 注解")
    void capabilityLayerMustNotKnowHttp() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..tools..", "..rag..", "..graph..", "..embedding..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta.servlet..",
                        "org.springframework.web..")
                .because("工具与检索要能脱离 HTTP 被复用（定时任务、消息消费、其它服务调用都会用到它们）");

        rule.check(mainClasses);
    }

    @Test
    @DisplayName("领域模型保持干净：domain 不得依赖框架")
    void domainModelStaysFrameworkFree() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.ai..",
                        "org.springframework.web..",
                        "com.alibaba.cloud.ai..")
                .because("实体是数据形状，不是流程；它一旦认识模型或 HTTP，任何一处改动都会顺着它传下去");

        rule.check(mainClasses);
    }
}
