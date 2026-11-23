/*
 * LibreClinica is distributed under the
 * GNU Lesser General Public License (GNU LGPL).

 * For details see: https://libreclinica.org/license
 * copyright (C) 2003 - 2011 Akaza Research
 * copyright (C) 2003 - 2019 OpenClinica
 * copyright (C) 2020 - 2026 LibreClinica
 */
package org.akaza.openclinica.controller;

import javax.servlet.http.HttpServletRequest;

import org.akaza.openclinica.bean.crf.CrfCreateRequest;
import org.akaza.openclinica.bean.crf.CrfCreateResponse;
import org.akaza.openclinica.bean.login.UserAccountBean;
import org.akaza.openclinica.bean.managestudy.StudyBean;
import org.akaza.openclinica.dao.managestudy.StudyDAO;
import org.akaza.openclinica.service.crfdata.CrfMetadataCreationService;
import org.apache.commons.dbcp.BasicDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequestMapping(value = "/auth/api/v1/crfs")
public class CrfController {

    @Autowired
    private CrfMetadataCreationService crfMetadataCreationService;

    @Autowired
    @Qualifier("dataSource")
    private BasicDataSource dataSource;

    @RequestMapping(value = "/{studyOid}", method = RequestMethod.POST)
    public ResponseEntity<CrfCreateResponse> createCrf(HttpServletRequest request, @PathVariable("studyOid") String studyOid,
            @RequestBody CrfCreateRequest body) {
        CrfCreateResponse response = new CrfCreateResponse();

        UserAccountBean owner = (UserAccountBean) request.getSession().getAttribute("userBean");
        if (owner == null || (!owner.isTechAdmin() && !owner.isSysAdmin())) {
            response.setMessage(CrfCreateResponse.VALIDATION_FAILED);
            response.getErrors().add("The Owner User Account is not a Valid Account or Does not have Admin user type");
            return new ResponseEntity<CrfCreateResponse>(response, HttpStatus.FORBIDDEN);
        }

        StudyBean study = findStudy(studyOid);
        if (study == null || study.getParentStudyId() != 0) {
            response.setMessage(CrfCreateResponse.VALIDATION_FAILED);
            response.getErrors().add("The Study OID provided in the URL is not a valid Study OID");
            return new ResponseEntity<CrfCreateResponse>(response, HttpStatus.BAD_REQUEST);
        }

        response = crfMetadataCreationService.createCrf(study.getId(), body, owner);
        if (CrfCreateResponse.VALIDATION_FAILED.equals(response.getMessage())) {
            return new ResponseEntity<CrfCreateResponse>(response, HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<CrfCreateResponse>(response, HttpStatus.OK);
    }

    protected StudyBean findStudy(String studyOid) {
        return new StudyDAO(dataSource).findByOid(studyOid);
    }
}
