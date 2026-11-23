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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.akaza.openclinica.bean.crf.CrfCreateRequest;
import org.akaza.openclinica.bean.crf.CrfCreateResponse;
import org.akaza.openclinica.bean.login.UserAccountBean;
import org.akaza.openclinica.dao.hibernate.CrfDao;
import org.akaza.openclinica.dao.hibernate.CrfVersionDao;
import org.akaza.openclinica.dao.hibernate.EventDefinitionCrfDao;
import org.akaza.openclinica.dao.hibernate.ItemDao;
import org.akaza.openclinica.dao.hibernate.ItemDataTypeDao;
import org.akaza.openclinica.dao.hibernate.ItemFormMetadataDao;
import org.akaza.openclinica.dao.hibernate.ItemGroupDao;
import org.akaza.openclinica.dao.hibernate.ItemGroupMetadataDao;
import org.akaza.openclinica.dao.hibernate.ItemReferenceTypeDao;
import org.akaza.openclinica.dao.hibernate.ResponseSetDao;
import org.akaza.openclinica.dao.hibernate.ResponseTypeDao;
import org.akaza.openclinica.dao.hibernate.SectionDao;
import org.akaza.openclinica.dao.hibernate.StudyDao;
import org.akaza.openclinica.dao.hibernate.StudyEventDefinitionDao;
import org.akaza.openclinica.dao.hibernate.UserAccountDao;
import org.akaza.openclinica.dao.hibernate.VersioningMapDao;
import org.akaza.openclinica.domain.datamap.CrfBean;
import org.akaza.openclinica.domain.datamap.CrfVersion;
import org.akaza.openclinica.domain.datamap.Item;
import org.akaza.openclinica.domain.datamap.ItemDataType;
import org.akaza.openclinica.domain.datamap.ItemReferenceType;
import org.akaza.openclinica.domain.datamap.ResponseSet;
import org.akaza.openclinica.domain.datamap.ResponseType;
import org.akaza.openclinica.domain.datamap.Section;
import org.akaza.openclinica.domain.datamap.Study;
import org.akaza.openclinica.domain.user.UserAccount;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class CrfMetadataCreationServiceTest {

    @Mock
    private CrfDao crfDao;
    @Mock
    private CrfVersionDao crfVersionDao;
    @Mock
    private SectionDao sectionDao;
    @Mock
    private ItemGroupDao itemGroupDao;
    @Mock
    private ItemDao itemDao;
    @Mock
    private ItemFormMetadataDao itemFormMetadataDao;
    @Mock
    private ItemGroupMetadataDao itemGroupMetadataDao;
    @Mock
    private ResponseSetDao responseSetDao;
    @Mock
    private VersioningMapDao versioningMapDao;
    @Mock
    private ItemDataTypeDao itemDataTypeDao;
    @Mock
    private ItemReferenceTypeDao itemRefTypeDao;
    @Mock
    private ResponseTypeDao responseTypeDao;
    @Mock
    private UserAccountDao userDao;
    @Mock
    private StudyDao studyDao;
    @Mock
    private EventDefinitionCrfDao eventDefinitionCrfDao;
    @Mock
    private StudyEventDefinitionDao studyEventDefinitionDao;

    @InjectMocks
    private CrfMetadataCreationService service;

    private UserAccountBean owner;

    @Before
    public void setUp() {
        owner = new UserAccountBean();
        owner.setId(1);

        when(userDao.findById(anyInt())).thenReturn(new UserAccount());
        when(studyDao.findById(anyInt())).thenReturn(new Study());
        when(crfDao.save(any(CrfBean.class))).thenReturn(1);
        when(crfVersionDao.save(any(CrfVersion.class))).thenReturn(1);
        when(itemGroupDao.save(any(org.akaza.openclinica.domain.datamap.ItemGroup.class))).thenReturn(1);
        when(itemDao.save(any(Item.class))).thenReturn(1);
        when(sectionDao.findByCrfVersionOrdinal(anyInt(), anyInt())).thenReturn(null, new Section());
        when(itemRefTypeDao.findByItemReferenceTypeId(anyInt())).thenReturn(new ItemReferenceType());
        when(itemDataTypeDao.findByItemDataTypeCode(anyString())).thenReturn(new ItemDataType());
        ResponseType textType = new ResponseType();
        textType.setName("text");
        when(responseTypeDao.findByResponseTypeName(anyString())).thenReturn(textType);
        when(responseSetDao.findByLabelVersion(anyString(), anyInt())).thenReturn(null, new ResponseSet());
    }

    private CrfCreateRequest validRequest() {
        CrfCreateRequest request = new CrfCreateRequest();
        request.setCrfName("enrollment");
        request.setVersionName("v1");
        CrfCreateRequest.Item item = new CrfCreateRequest.Item();
        item.setName("record_id");
        item.setLabel("Record ID");
        item.setType("text");
        item.setRequired(true);
        List<CrfCreateRequest.Item> items = new ArrayList<CrfCreateRequest.Item>();
        items.add(item);
        request.setItems(items);
        return request;
    }

    @Test
    public void createsCrfVersionAndItem() {
        CrfCreateResponse response = service.createCrf(1, validRequest(), owner);
        assertThat(response.getMessage(), is(CrfCreateResponse.SUCCESS));
        assertThat(response.getCrfOid(), is("F_ENROLLMENT"));
        assertThat(response.getItemOids().get(0), is("I_RECORD_ID"));
        verify(crfDao, times(1)).save(any(CrfBean.class));
        verify(crfVersionDao, times(1)).save(any(CrfVersion.class));
        verify(itemDao, times(1)).save(any(Item.class));
        verify(itemFormMetadataDao, times(1)).saveOrUpdate(any(org.akaza.openclinica.domain.datamap.ItemFormMetadata.class));
    }

    @Test
    public void rejectsMissingCrfName() {
        CrfCreateRequest request = validRequest();
        request.setCrfName("");
        CrfCreateResponse response = service.createCrf(1, request, owner);
        assertThat(response.getMessage(), is(CrfCreateResponse.VALIDATION_FAILED));
        verify(crfDao, never()).save(any(CrfBean.class));
    }

    @Test
    public void reusesExistingCrfByIdempotentOid() {
        CrfBean existing = new CrfBean();
        existing.setCrfId(5);
        existing.setOcOid("F_ENROLLMENT");
        when(crfDao.findByOcOID("F_ENROLLMENT")).thenReturn(existing);
        CrfCreateResponse response = service.createCrf(1, validRequest(), owner);
        assertThat(response.getMessage(), is(CrfCreateResponse.SUCCESS));
        verify(crfDao, never()).save(any(CrfBean.class));
    }

    @Test
    public void oidSanitizesAndCaps() {
        assertThat(CrfMetadataCreationService.oid("I_", "record id"), is("I_RECORD_ID"));
        assertThat(CrfMetadataCreationService.oid("F_", "dose-log"), is("F_DOSE_LOG"));
        String longOid = CrfMetadataCreationService.oid("I_", "abcdefghijklmnopqrstuvwxyz0123456789abcdefghij");
        assertThat(longOid.length() <= 40, is(true));
    }
}
