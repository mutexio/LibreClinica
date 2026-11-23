/*
 * LibreClinica is distributed under the
 * GNU Lesser General Public License (GNU LGPL).

 * For details see: https://libreclinica.org/license
 * copyright (C) 2003 - 2011 Akaza Research
 * copyright (C) 2003 - 2019 OpenClinica
 * copyright (C) 2020 - 2026 LibreClinica
 */
package org.akaza.openclinica.bean.crf;

import java.util.ArrayList;
import java.util.List;

public class CrfCreateResponse {

    public static final String SUCCESS = "SUCCESS";
    public static final String VALIDATION_FAILED = "VALIDATION FAILED";

    private String message;
    private String crfOid;
    private String crfVersionOid;
    private List<String> itemOids = new ArrayList<String>();
    private List<String> errors = new ArrayList<String>();

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCrfOid() {
        return crfOid;
    }

    public void setCrfOid(String crfOid) {
        this.crfOid = crfOid;
    }

    public String getCrfVersionOid() {
        return crfVersionOid;
    }

    public void setCrfVersionOid(String crfVersionOid) {
        this.crfVersionOid = crfVersionOid;
    }

    public List<String> getItemOids() {
        return itemOids;
    }

    public void setItemOids(List<String> itemOids) {
        this.itemOids = itemOids;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
}
