/*
 * LibreClinica is distributed under the
 * GNU Lesser General Public License (GNU LGPL).

 * For details see: https://libreclinica.org/license
 * copyright (C) 2003 - 2011 Akaza Research
 * copyright (C) 2003 - 2019 OpenClinica
 * copyright (C) 2020 - 2026 LibreClinica
 */
package org.akaza.openclinica.service.crfdata;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.akaza.openclinica.bean.crf.CrfCreateRequest;
import org.akaza.openclinica.bean.crf.CrfCreateResponse;
import org.akaza.openclinica.bean.crf.OdmImportResponse;
import org.akaza.openclinica.bean.login.UserAccountBean;
import org.akaza.openclinica.dao.hibernate.StudyDao;
import org.akaza.openclinica.dao.hibernate.StudyEventDefinitionDao;
import org.akaza.openclinica.dao.hibernate.UserAccountDao;
import org.akaza.openclinica.domain.datamap.Study;
import org.akaza.openclinica.domain.datamap.StudyEventDefinition;
import org.akaza.openclinica.domain.user.UserAccount;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class OdmStudyImportServiceTest {

    private static final String ODM_XML = "<ODM xmlns=\"http://www.cdisc.org/ns/odm/v1.3\" ODMVersion=\"1.3\" FileOID=\"test\" "
            + "FileType=\"Snapshot\" CreationDateTime=\"2026-01-01T00:00:00\">"
            + "<Study OID=\"S_TEST\"><GlobalVariables><StudyName>Test</StudyName><StudyDescription>Test</StudyDescription>"
            + "<ProtocolName>test</ProtocolName></GlobalVariables>"
            + "<MetaDataVersion OID=\"v1\" Name=\"v1\">"
            + "<StudyEventDef OID=\"E_SCREENING\" Name=\"screening\" Repeating=\"No\" Type=\"Scheduled\">"
            + "<FormRef FormOID=\"F_ENROLLMENT\"/></StudyEventDef>"
            + "<FormDef OID=\"F_ENROLLMENT\" Name=\"enrollment\" Repeating=\"No\">"
            + "<ItemGroupRef ItemGroupOID=\"IG_ENROLLMENT\"/></FormDef>"
            + "<ItemGroupDef OID=\"IG_ENROLLMENT\" Name=\"enrollment\" Repeating=\"No\">"
            + "<ItemRef ItemOID=\"I_RECORD_ID\" Mandatory=\"Yes\"/>"
            + "<ItemRef ItemOID=\"I_DOSE_PRODUCT\" Mandatory=\"No\"/></ItemGroupDef>"
            + "<ItemDef OID=\"I_RECORD_ID\" Name=\"record_id\" DataType=\"text\">"
            + "<Description><TranslatedText>Record ID</TranslatedText></Description></ItemDef>"
            + "<ItemDef OID=\"I_DOSE_PRODUCT\" Name=\"dose_product\" DataType=\"text\">"
            + "<Description><TranslatedText>Study product</TranslatedText></Description>"
            + "<CodeListRef CodeListOID=\"CL_PRODUCT\"/></ItemDef>"
            + "<CodeList OID=\"CL_PRODUCT\" Name=\"product\" DataType=\"text\">"
            + "<CodeListItem CodedValue=\"1\"><Decode><TranslatedText>Product A</TranslatedText></Decode></CodeListItem>"
            + "<CodeListItem CodedValue=\"2\"><Decode><TranslatedText>Product B</TranslatedText></Decode></CodeListItem>"
            + "</CodeList></MetaDataVersion></Study></ODM>";

    @Mock
    private CrfMetadataCreationService crfMetadataCreationService;
    @Mock
    private StudyEventDefinitionDao studyEventDefinitionDao;
    @Mock
    private StudyDao studyDao;
    @Mock
    private UserAccountDao userDao;

    @InjectMocks
    private OdmStudyImportService service;

    private UserAccountBean owner;

    @Before
    public void setUp() {
        owner = new UserAccountBean();
        owner.setId(1);
        when(studyDao.findById(anyInt())).thenReturn(new Study());
        when(userDao.findById(anyInt())).thenReturn(new UserAccount());
        when(studyEventDefinitionDao.save(any(StudyEventDefinition.class))).thenReturn(1);
        CrfCreateResponse result = new CrfCreateResponse();
        result.setMessage(CrfCreateResponse.SUCCESS);
        result.setCrfOid("F_ENROLLMENT");
        result.setCrfVersionOid("F_ENROLLMENT_V1");
        when(crfMetadataCreationService.createCrf(anyInt(), any(CrfCreateRequest.class), any(UserAccountBean.class))).thenReturn(result);
    }

    @Test
    public void importsEventsFormsAndDesignations() {
        OdmImportResponse response = service.importStudy(1, ODM_XML, owner);
        assertThat(response.getMessage(), is(OdmImportResponse.SUCCESS));
        assertThat(response.getEventOids().get(0), is("E_SCREENING"));
        assertThat(response.getCrfOids().get(0), is("F_ENROLLMENT"));
        verify(studyEventDefinitionDao, times(1)).save(any(StudyEventDefinition.class));
        verify(crfMetadataCreationService, times(1)).createCrf(anyInt(), any(CrfCreateRequest.class), any(UserAccountBean.class));
        verify(crfMetadataCreationService).designate(eq("E_SCREENING"), eq("F_ENROLLMENT"), eq("F_ENROLLMENT_V1"), eq(1), any(UserAccountBean.class));
    }

    @Test
    public void mapsItemsAndChoicesFromOdm() {
        service.importStudy(1, ODM_XML, owner);
        ArgumentCaptor<CrfCreateRequest> captor = ArgumentCaptor.forClass(CrfCreateRequest.class);
        verify(crfMetadataCreationService).createCrf(anyInt(), captor.capture(), any(UserAccountBean.class));
        CrfCreateRequest request = captor.getValue();
        assertThat(request.getCrfName(), is("enrollment"));
        assertThat(request.getItems().size(), is(2));
        CrfCreateRequest.Item coded = request.getItems().get(1);
        assertThat(coded.getName(), is("dose_product"));
        assertThat(coded.getType(), is("radio"));
        assertThat(coded.getChoices().size(), is(2));
        assertThat(coded.getChoices().get(0).getCode(), is("1"));
        assertThat(coded.getChoices().get(0).getLabel(), is("Product A"));
        assertThat(request.getItems().get(0).isRequired(), is(true));
    }

    @Test
    public void reusesExistingEventByIdempotentOid() {
        StudyEventDefinition existing = new StudyEventDefinition();
        existing.setStudyEventDefinitionId(9);
        when(studyEventDefinitionDao.findByOcOID("E_SCREENING")).thenReturn(existing);
        OdmImportResponse response = service.importStudy(1, ODM_XML, owner);
        assertThat(response.getMessage(), is(OdmImportResponse.SUCCESS));
        verify(studyEventDefinitionDao, never()).save(any(StudyEventDefinition.class));
    }

    @Test
    public void rejectsInvalidXml() {
        OdmImportResponse response = service.importStudy(1, "not-xml", owner);
        assertThat(response.getMessage(), is(OdmImportResponse.VALIDATION_FAILED));
        verify(crfMetadataCreationService, never()).createCrf(anyInt(), any(CrfCreateRequest.class), any(UserAccountBean.class));
    }
}
