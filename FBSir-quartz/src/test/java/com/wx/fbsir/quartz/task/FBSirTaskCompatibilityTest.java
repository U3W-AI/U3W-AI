package com.wx.fbsir.quartz.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import com.wx.fbsir.common.utils.spring.SpringUtils;
import com.wx.fbsir.quartz.domain.SysJob;
import com.wx.fbsir.quartz.util.JobInvokeUtil;
import com.wx.fbsir.quartz.util.ScheduleUtils;

@SuppressWarnings("deprecation")
class FBSirTaskCompatibilityTest
{
    private AnnotationConfigApplicationContext context;
    private PrintStream originalOut;
    private ByteArrayOutputStream output;

    @BeforeEach
    void setUp()
    {
        context = new AnnotationConfigApplicationContext();
        context.register(SpringUtils.class, FBSirTask.class, WxFbsirTask.class);
        context.refresh();
        originalOut = System.out;
        output = new ByteArrayOutputStream();
        System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown()
    {
        System.setOut(originalOut);
        context.close();
    }

    @Test
    void registersBothNewAndLegacyBeanNames()
    {
        assertNotNull(context.getBean("FBSirTask"));
        assertNotNull(context.getBean("WxFbsirTask"));
        assertTrue(ScheduleUtils.whiteList("FBSirTask.FBSirNoParams"));
        assertTrue(ScheduleUtils.whiteList("WxFbsirTask.WxFbsirNoParams"));
    }

    @Test
    void legacyTargetsDelegateToTheNewTaskImplementation() throws Exception
    {
        assertSameOutput("FBSirTask.FBSirNoParams", "WxFbsirTask.WxFbsirNoParams");
        assertSameOutput("FBSirTask.FBSirParams('FBSir')", "WxFbsirTask.WxFbsirParams('FBSir')");
        assertSameOutput(
                "FBSirTask.FBSirMultipleParams('FBSir', true, 2000L, 316.50D, 100)",
                "WxFbsirTask.WxFbsirMultipleParams('FBSir', true, 2000L, 316.50D, 100)");
    }

    private void assertSameOutput(String newTarget, String legacyTarget) throws Exception
    {
        String newOutput = invokeAndRead(newTarget);
        String legacyOutput = invokeAndRead(legacyTarget);
        assertEquals(newOutput, legacyOutput);
    }

    private String invokeAndRead(String invokeTarget) throws Exception
    {
        output.reset();
        SysJob job = new SysJob();
        job.setInvokeTarget(invokeTarget);
        JobInvokeUtil.invokeMethod(job);
        return output.toString(StandardCharsets.UTF_8);
    }
}
