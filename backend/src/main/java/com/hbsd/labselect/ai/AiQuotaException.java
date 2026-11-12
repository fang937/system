package com.hbsd.labselect.ai;

import com.hbsd.labselect.common.BizException;

/**
 * 额度类错误（余额不足、配额用尽、限流到顶）。
 * 网关据此把通道标记为「额度不足」并切换到下一个通道，前端据此显示红色提示。
 */
public class AiQuotaException extends BizException {

    public AiQuotaException(String provider, String detail) {
        super(429, "「" + provider + "」额度不足或已限流：" + detail);
    }
}
