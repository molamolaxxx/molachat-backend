package com.mola.molachat.session.model;

import lombok.Data;

/**
 * @author : molamola
 * @Project: molachat
 * @Description:
 * @date : 2025-02-15 13:48
 **/
@Data
public class StreamMessage extends Message {

    private String streamId;

    /**
     * 打开模态框
     */
    private boolean openViewModal;

    private boolean end;
}
