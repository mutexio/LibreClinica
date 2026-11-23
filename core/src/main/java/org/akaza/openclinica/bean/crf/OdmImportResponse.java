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

public class OdmImportResponse {

    public static final String SUCCESS = "SUCCESS";
    public static final String VALIDATION_FAILED = "VALIDATION FAILED";

    private String message;
    private List<String> eventOids = new ArrayList<String>();
    private List<String> crfOids = new ArrayList<String>();
    private List<String> errors = new ArrayList<String>();

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<String> getEventOids() {
        return eventOids;
    }

    public void setEventOids(List<String> eventOids) {
        this.eventOids = eventOids;
    }

    public List<String> getCrfOids() {
        return crfOids;
    }

    public void setCrfOids(List<String> crfOids) {
        this.crfOids = crfOids;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }
}
