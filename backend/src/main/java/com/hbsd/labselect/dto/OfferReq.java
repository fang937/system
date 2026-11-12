package com.hbsd.labselect.dto;

/**
 * 阶段 4：导师处置结果。
 * offerStatus: 1 拟录取（占名额并发 Offer）/ 2 递补 / 3 淘汰 / 0 撤销回待处理
 */
public record OfferReq(Integer offerStatus, String remark) {
}
