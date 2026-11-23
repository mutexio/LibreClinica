/*
 * LibreClinica is distributed under the
 * GNU Lesser General Public License (GNU LGPL).

 * For details see: https://libreclinica.org/license
 * copyright (C) 2003 - 2011 Akaza Research
 * copyright (C) 2003 - 2019 OpenClinica
 * copyright (C) 2020 - 2026 LibreClinica
 */
package org.akaza.openclinica.controller;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.akaza.openclinica.bean.core.UserType;
import org.akaza.openclinica.bean.crf.CrfCreateRequest;
import org.akaza.openclinica.bean.crf.CrfCreateResponse;
import org.akaza.openclinica.bean.login.UserAccountBean;
import org.akaza.openclinica.bean.managestudy.StudyBean;
import org.akaza.openclinica.service.crfdata.CrfMetadataCreationService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(MockitoJUnitRunner.class)
public class CrfControllerTest {

    private CrfMetadataCreationService service;
    private CrfController controller;
    private HttpServletRequest request;
    private HttpSession session;

    @Before
    public void setUp() {
        service = mock(CrfMetadataCreationService.class);
        controller = new CrfController() {
            @Override
            protected StudyBean findStudy(String studyOid) {
                StudyBean study = new StudyBean();
                study.setId(1);
                study.setParentStudyId(0);
                return study;
            }
        };
        ReflectionTestUtils.setField(controller, "crfMetadataCreationService", service);
        request = mock(HttpServletRequest.class);
        session = mock(HttpSession.class);
        when(request.getSession()).thenReturn(session);
    }

    private UserAccountBean admin() {
        UserAccountBean owner = new UserAccountBean();
        owner.setId(1);
        owner.addUserType(UserType.SYSADMIN);
        return owner;
    }

    private CrfCreateRequest body() {
        CrfCreateRequest body = new CrfCreateRequest();
        body.setCrfName("enrollment");
        return body;
    }

    @Test
    public void rejectsNonAdmin() {
        UserAccountBean plain = new UserAccountBean();
        plain.setId(2);
        when(session.getAttribute("userBean")).thenReturn(plain);
        ResponseEntity<CrfCreateResponse> response = controller.createCrf(request, "S_TEST", body());
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        assertEquals(CrfCreateResponse.VALIDATION_FAILED, response.getBody().getMessage());
        verify(service, never()).createCrf(anyInt(), any(CrfCreateRequest.class), any(UserAccountBean.class));
    }

    @Test
    public void rejectsMissingUser() {
        when(session.getAttribute("userBean")).thenReturn(null);
        ResponseEntity<CrfCreateResponse> response = controller.createCrf(request, "S_TEST", body());
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(service, never()).createCrf(anyInt(), any(CrfCreateRequest.class), any(UserAccountBean.class));
    }

    @Test
    public void acceptsAdminAndDelegates() {
        when(session.getAttribute("userBean")).thenReturn(admin());
        CrfCreateResponse success = new CrfCreateResponse();
        success.setMessage(CrfCreateResponse.SUCCESS);
        success.setCrfOid("F_ENROLLMENT");
        when(service.createCrf(anyInt(), any(CrfCreateRequest.class), any(UserAccountBean.class))).thenReturn(success);
        ResponseEntity<CrfCreateResponse> response = controller.createCrf(request, "S_TEST", body());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("F_ENROLLMENT", response.getBody().getCrfOid());
        verify(service).createCrf(anyInt(), any(CrfCreateRequest.class), any(UserAccountBean.class));
    }
}
