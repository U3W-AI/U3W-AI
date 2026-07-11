package com.wx.fbsir.quartz.task;

import com.wx.fbsir.common.utils.StringUtils;
import org.springframework.stereotype.Component;

/**
 * FBSir Quartz scheduling sample tasks.
 */
@Component("FBSirTask")
public class FBSirTask
{
    public void FBSirMultipleParams(String s, Boolean b, Long l, Double d, Integer i)
    {
        System.out.println(StringUtils.format(
                "FBSir scheduled task executed: string={}, boolean={}, long={}, double={}, integer={}",
                s, b, l, d, i));
    }

    public void FBSirParams(String params)
    {
        System.out.println("FBSir scheduled task executed with parameter: " + params);
    }

    public void FBSirNoParams()
    {
        System.out.println("FBSir scheduled task executed without parameters");
    }
}
