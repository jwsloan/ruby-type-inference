import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.yourkit.util.FileUtil;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.exposed.sql.Database;
import org.jetbrains.exposed.sql.SchemaUtils;
import org.jetbrains.exposed.sql.Transaction;
import org.jetbrains.exposed.sql.transactions.ThreadLocalTransactionManager;
import org.jetbrains.exposed.sql.transactions.TransactionManager;
import org.jetbrains.plugins.ruby.ruby.run.LocalRunner;
import org.jetbrains.plugins.ruby.ruby.run.RubyCommandLine;
import org.jetbrains.ruby.codeInsight.types.signature.*;
import org.jetbrains.ruby.codeInsight.types.signature.contractTransition.ContractTransition;
import org.jetbrains.ruby.codeInsight.types.signature.serialization.SignatureContractSerializationKt;
import org.jetbrains.ruby.codeInsight.types.signature.serialization.StringDataOutput;
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.ClassInfoTable;
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.GemInfoTable;
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.MethodInfoTable;
import org.jetbrains.ruby.codeInsight.types.storage.server.impl.SignatureTable;
import org.jetbrains.ruby.runtime.signature.server.SignatureServer;
import org.junit.Assert;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.jetbrains.exposed.sql.transactions.TransactionApiKt.DEFAULT_ISOLATION_LEVEL;

public class CallStatCompletionTest extends LightPlatformCodeInsightFixtureTestCase {

    private static final Logger LOGGER = Logger.getInstance("CallStatCompletionTest");

    @Override
    protected String getTestDataPath() {
        return "src/test/testData";
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Database.Companion.connect("jdbc:h2:mem:test", "org.h2.Driver", "", "", connection -> Unit.INSTANCE,
                database -> {
                    ThreadLocalTransactionManager manager = new ThreadLocalTransactionManager(database, DEFAULT_ISOLATION_LEVEL);
                    TransactionManager.Companion.setManager(manager);
                    return manager;
                });
        final Transaction transaction = TransactionManager.Companion.getManager().newTransaction(4);
        SchemaUtils.INSTANCE.create(GemInfoTable.INSTANCE, ClassInfoTable.INSTANCE, MethodInfoTable.INSTANCE, SignatureTable.INSTANCE);
        transaction.commit();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            SchemaUtils.INSTANCE.drop(GemInfoTable.INSTANCE, ClassInfoTable.INSTANCE, MethodInfoTable.INSTANCE, SignatureTable.INSTANCE);
        } finally {
            super.tearDown();
        }
    }

    public void testSimple() {
        doTest("sample_test", createMethodInfo("A", "foo"), "test1", "test2");
    }

    public void testKW() {
        doTest("sample_kw_test", createMethodInfo("A", "foo1"), "test1", "test2");
    }

    public void testMultipleExecution() {
        executeScript("multiple_execution_test1.rb");
        SignatureContract contract = doTestContract("multiple_execution_test2",
                createMethodInfo("A", "foo2"));


        assertEquals(3, contract.getNodeCount());
        Map<ContractTransition, SignatureNode> edges = contract.getStartNode().getTransitions();
        assertEquals(4, edges.size());

        assertEquals(1, edges.values().stream().distinct().count());
        Map<ContractTransition, SignatureNode> nextEdges = edges.values().iterator().next().getTransitions();

        assertEquals(Collections.singleton("Abacaba"), nextEdges.keySet().iterator().next().getValue(Collections.singletonList(Collections.singleton("Abacaba"))));
    }

    public void testRefLinks() {
        SignatureContract contract = doTestContract("ref_links_test",
                createMethodInfo("A", "doo"));

        final StringDataOutput stream = new StringDataOutput();
        SignatureContractSerializationKt.serialize(contract, stream);
        assertTrue(
                stream.getResult().toString().equals("3 a 0 b 0 c 0 9 3 1 0 Fixnum 2 0 B 3 0 String 1 4 0 String 1 4 0 A 1 5 1 1 1 6 1 1 1 7 1 3 1 8 1 5 1 8 1 7 0")
                || stream.getResult().toString().equals("3 a 0 b 0 c 0 9 3 1 0 Integer 2 0 B 3 0 String 1 4 0 String 1 4 0 A 1 5 1 1 1 6 1 1 1 7 1 3 1 8 1 5 1 8 1 7 0")
        );
    }

    private void executeScript(@NotNull String runnableScriptName) {
        final String scriptPath = PathManager.getAbsolutePath("ide-plugin/" + getTestDataPath() + "/" + runnableScriptName);

        final Module module = myFixture.getModule();

        try {
            LOGGER.warn(getProcessOutput(new RubyCommandLine(LocalRunner.getRunner(module), false)
                    .withWorkDirectory("../arg_scanner")
                    .withExePath("rake")
                    .withParameters("install")
                    .createProcess()));

            LOGGER.warn(getProcessOutput(new RubyCommandLine(LocalRunner.getRunner(module), false)
                    .withExePath("arg-scanner")
                    .withParameters("ruby", scriptPath)
                    .createProcess()));
        } catch (ExecutionException | InterruptedException e) {
            LOGGER.error(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static String getProcessOutput(@NotNull Process process) throws InterruptedException {
//        final InputStream inputStream = process.getInputStream();
        final InputStream errorStream = process.getErrorStream();
        process.waitFor(30, TimeUnit.SECONDS);
        try {
            return StringUtil.join(FileUtil.readStreamAsLines(errorStream), "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private SignatureContract run(@NotNull String name, @NotNull MethodInfo methodInfo) {
        final String scriptName = name + ".rb";
        final String runnableScriptName = name + "_to_run.rb";

        myFixture.configureByFiles(scriptName, runnableScriptName);

        SignatureServer callStatServer = SignatureServer.INSTANCE;
        executeScript(runnableScriptName);

        try {
            Thread.sleep(100);
        } catch (InterruptedException ignored) {
        }

        int cnt = 0;
        while (callStatServer.isProcessingRequests() && cnt < 100) {
            try {
                Thread.sleep(1000);
                cnt++;
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        return callStatServer.getContract(methodInfo);
    }

    private void doTest(@NotNull String name, @NotNull MethodInfo methodInfo, String... items) {

        final String scriptName = name + ".rb";
        Assert.assertNotNull(run(name, methodInfo));

        myFixture.testCompletionVariants(scriptName, items);
    }

    private SignatureContract doTestContract(@NotNull String name, @NotNull MethodInfo methodInfo) {
        SignatureContract contract = run(name, methodInfo);
        Assert.assertNotNull(contract);

        return contract;
    }

    private static MethodInfo createMethodInfo(@NotNull String className, @NotNull String methodName) {
        return MethodInfoKt.MethodInfo(ClassInfoKt.ClassInfo(className), methodName, RVisibility.PUBLIC);
    }

}