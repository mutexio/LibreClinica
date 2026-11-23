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

import org.akaza.openclinica.bean.core.Role;
import org.akaza.openclinica.bean.core.UserType;
import org.akaza.openclinica.bean.crf.CrfCreateRequest;
import org.akaza.openclinica.bean.crf.CrfCreateResponse;
import org.akaza.openclinica.bean.login.StudyUserRoleBean;
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
    private StudyUserRoleBean userRole;

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

            @Override
            protected StudyUserRoleBean getUserRole(UserAccountBean owner, StudyBean study) {
                return userRole;
            }
        };
        ReflectionTestUtils.setField(controller, "crfMetadataCreationService", service);
        request = mock(HttpServletRequest.class);
        session = mock(HttpSession.class);
        when(request.getSession()).thenReturn(session);
        userRole = null;
    }

    private UserAccountBean plainUser() {
        UserAccountBean owner = new UserAccountBean();
        owner.setId(2);
        owner.setName("root");
        owner.addUserType(UserType.USER);
        return owner;
    }

    private UserAccountBean admin() {
        UserAccountBean owner = plainUser();
        owner.addUserType(UserType.SYSADMIN);
        return owner;
    }

    private void withRole(Role role) {
        userRole = mock(StudyUserRoleBean.class);
        when(userRole.getRole()).thenReturn(role);
    }

    private CrfCreateRequest body() {
        CrfCreateRequest body = new CrfCreateRequest();
        body.setCrfName("enrollment");
        return body;
    }

    private void stubSuccess() {
        CrfCreateResponse success = new CrfCreateResponse();
        success.setMessage(CrfCreateResponse.SUCCESS);
        success.setCrfOid("F_ENROLLMENT");
        when(service.createCrf(anyInt(), any(CrfCreateRequest.class), any(UserAccountBean.class))).thenReturn(success);
    }

    @Test
    public void rejectsMissingUser() {
        when(session.getAttribute("userBean")).thenReturn(null);
        ResponseEntity<CrfCreateResponse> response = controller.createCrf(request, "S_TEST", body());
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(service, never()).createCrf(anyInt(), any(CrfCreateRequest.class), any(UserAccountBean.class));
    }

    @Test
    public void rejectsUserWithNoStudyRole() {
        when(session.getAttribute("userBean")).thenReturn(plainUser());
        ResponseEntity<CrfCreateResponse> response = controller.createCrf(request, "S_TEST", body());
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(service, never()).createCrf(anyInt(), any(CrfCreateRequest.class), any(UserAccountBean.class));
    }

    @Test
    public void rejectsDataEntryRole() {
        when(session.getAttribute("userBean")).thenReturn(plainUser());
        withRole(Role.RESEARCHASSISTANT);
        ResponseEntity<CrfCreateResponse> response = controller.createCrf(request, "S_TEST", body());
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(service, never()).createCrf(anyInt(), any(CrfCreateRequest.class), any(UserAccountBean.class));
    }

    @Test
    public void acceptsGlobalAdmin() {
        when(session.getAttribute("userBean")).thenReturn(admin());
        stubSuccess();
        ResponseEntity<CrfCreateResponse> response = controller.createCrf(request, "S_TEST", body());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(service).createCrf(anyInt(), any(CrfCreateRequest.class), any(UserAccountBean.class));
    }

    @Test
    public void acceptsStudyDirector() {
        when(session.getAttribute("userBean")).thenReturn(plainUser());
        withRole(Role.STUDYDIRECTOR);
        stubSuccess();
        ResponseEntity<CrfCreateResponse> response = controller.createCrf(request, "S_TEST", body());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(service).createCrf(anyInt(), any(CrfCreateRequest.class), any(UserAccountBean.class));
    }

    @Test
    public void acceptsStudyCoordinator() {
        when(session.getAttribute("userBean")).thenReturn(plainUser());
        withRole(Role.COORDINATOR);
        stubSuccess();
        ResponseEntity<CrfCreateResponse> response = controller.createCrf(request, "S_TEST", body());
        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(service).createCrf(anyInt(), any(CrfCreateRequest.class), any(UserAccountBean.class));
    }
}
