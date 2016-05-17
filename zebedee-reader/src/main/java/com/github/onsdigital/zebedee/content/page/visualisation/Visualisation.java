package com.github.onsdigital.zebedee.content.page.visualisation;

import com.github.onsdigital.zebedee.content.page.base.Page;
import com.github.onsdigital.zebedee.content.page.base.PageType;

/**
 * Created by crispin on 16/05/2016.
 */
public class Visualisation extends Page {

    private String uid;

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public String fileUri;

    public String getFileUri() {
        return fileUri;
    }

    public void setFileUri(String fileUri) {
        this.fileUri = fileUri;
    }

    @Override
    public PageType getType() {
        return PageType.visualisation;
    }
}
