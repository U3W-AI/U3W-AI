package com.wx.fbsir.quartz.task;

import org.springframework.stereotype.Component;

/**
 * Compatibility facade for Quartz jobs persisted with the former task name.
 *
 * @deprecated use {@link FBSirTask}. This bean must remain available until all
 *             persisted {@code sys_job.invoke_target} values have migrated.
 */
@Deprecated(since = "1.0.0")
@Component("WxFbsirTask")
public class WxFbsirTask
{
    private final FBSirTask delegate;

    public WxFbsirTask(FBSirTask delegate)
    {
        this.delegate = delegate;
    }

    /**
     * @deprecated use {@link FBSirTask#FBSirMultipleParams(String, Boolean, Long, Double, Integer)}.
     */
    @Deprecated(since = "1.0.0")
    public void WxFbsirMultipleParams(String s, Boolean b, Long l, Double d, Integer i)
    {
        delegate.FBSirMultipleParams(s, b, l, d, i);
    }

    /**
     * @deprecated use {@link FBSirTask#FBSirParams(String)}.
     */
    @Deprecated(since = "1.0.0")
    public void WxFbsirParams(String params)
    {
        delegate.FBSirParams(params);
    }

    /**
     * @deprecated use {@link FBSirTask#FBSirNoParams()}.
     */
    @Deprecated(since = "1.0.0")
    public void WxFbsirNoParams()
    {
        delegate.FBSirNoParams();
    }
}
