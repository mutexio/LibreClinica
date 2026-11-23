/*
 * LibreClinica is distributed under the
 * GNU Lesser General Public License (GNU LGPL).

 * For details see: https://libreclinica.org/license
 * copyright (C) 2003 - 2011 Akaza Research
 * copyright (C) 2003 - 2019 OpenClinica
 * copyright (C) 2020 - 2026 LibreClinica
 */
package org.akaza.openclinica.service.crfdata;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;

import org.akaza.openclinica.bean.crf.CrfCreateRequest;
import org.akaza.openclinica.bean.crf.CrfCreateResponse;
import org.akaza.openclinica.bean.crf.OdmImportResponse;
import org.akaza.openclinica.bean.login.UserAccountBean;
import org.akaza.openclinica.dao.hibernate.StudyDao;
import org.akaza.openclinica.dao.hibernate.StudyEventDefinitionDao;
import org.akaza.openclinica.dao.hibernate.UserAccountDao;
import org.akaza.openclinica.domain.Status;
import org.akaza.openclinica.domain.datamap.Study;
import org.akaza.openclinica.domain.datamap.StudyEventDefinition;
import org.akaza.openclinica.domain.user.UserAccount;
import org.cdisc.ns.odm.v130.ODM;
import org.cdisc.ns.odm.v130.ODMcomplexTypeDefinitionCodeList;
import org.cdisc.ns.odm.v130.ODMcomplexTypeDefinitionCodeListItem;
import org.cdisc.ns.odm.v130.ODMcomplexTypeDefinitionDescription;
import org.cdisc.ns.odm.v130.ODMcomplexTypeDefinitionFormDef;
import org.cdisc.ns.odm.v130.ODMcomplexTypeDefinitionFormRef;
import org.cdisc.ns.odm.v130.ODMcomplexTypeDefinitionItemDef;
import org.cdisc.ns.odm.v130.ODMcomplexTypeDefinitionItemGroupDef;
import org.cdisc.ns.odm.v130.ODMcomplexTypeDefinitionItemGroupRef;
import org.cdisc.ns.odm.v130.ODMcomplexTypeDefinitionItemRef;
import org.cdisc.ns.odm.v130.ODMcomplexTypeDefinitionMetaDataVersion;
import org.cdisc.ns.odm.v130.ODMcomplexTypeDefinitionStudy;
import org.cdisc.ns.odm.v130.ODMcomplexTypeDefinitionStudyEventDef;
import org.cdisc.ns.odm.v130.ODMcomplexTypeDefinitionTranslatedText;
import org.cdisc.ns.odm.v130.YesOrNo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OdmStudyImportService {

    private static final JAXBContext ODM_CONTEXT;
    static {
        try {
            ODM_CONTEXT = JAXBContext.newInstance(ODM.class);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create ODM JAXB context", e);
        }
    }

    @Autowired
    private CrfMetadataCreationService crfMetadataCreationService;
    @Autowired
    private StudyEventDefinitionDao studyEventDefinitionDao;
    @Autowired
    private StudyDao studyDao;
    @Autowired
    private UserAccountDao userDao;

    @Transactional
    public OdmImportResponse importStudy(int studyId, String odmXml, UserAccountBean owner) {
        OdmImportResponse response = new OdmImportResponse();
        ODM odm;
        try {
            Unmarshaller unmarshaller = ODM_CONTEXT.createUnmarshaller();
            odm = (ODM) unmarshaller.unmarshal(new StringReader(odmXml));
        } catch (Exception e) {
            response.setMessage(OdmImportResponse.VALIDATION_FAILED);
            response.getErrors().add("Invalid ODM XML: " + e.getMessage());
            return response;
        }

        if (odm.getStudy() == null || odm.getStudy().isEmpty()) {
            response.setMessage(OdmImportResponse.VALIDATION_FAILED);
            response.getErrors().add("ODM contains no Study element");
            return response;
        }
        ODMcomplexTypeDefinitionStudy study = odm.getStudy().get(0);
        if (study.getMetaDataVersion() == null || study.getMetaDataVersion().isEmpty()) {
            response.setMessage(OdmImportResponse.VALIDATION_FAILED);
            response.getErrors().add("ODM Study contains no MetaDataVersion");
            return response;
        }
        ODMcomplexTypeDefinitionMetaDataVersion version = study.getMetaDataVersion().get(0);

        Study domainStudy = studyDao.findById(studyId);
        UserAccount user = userDao.findById(owner.getId());

        Map<String, ODMcomplexTypeDefinitionItemGroupDef> itemGroups = new HashMap<String, ODMcomplexTypeDefinitionItemGroupDef>();
        for (ODMcomplexTypeDefinitionItemGroupDef group : version.getItemGroupDef()) {
            itemGroups.put(group.getOID(), group);
        }
        Map<String, ODMcomplexTypeDefinitionItemDef> items = new HashMap<String, ODMcomplexTypeDefinitionItemDef>();
        for (ODMcomplexTypeDefinitionItemDef item : version.getItemDef()) {
            items.put(item.getOID(), item);
        }
        Map<String, ODMcomplexTypeDefinitionCodeList> codeLists = new HashMap<String, ODMcomplexTypeDefinitionCodeList>();
        for (ODMcomplexTypeDefinitionCodeList codeList : version.getCodeList()) {
            codeLists.put(codeList.getOID(), codeList);
        }

        int ordinal = 1;
        for (ODMcomplexTypeDefinitionStudyEventDef eventDef : version.getStudyEventDef()) {
            createEvent(eventDef, domainStudy, user, owner, ordinal);
            response.getEventOids().add(eventDef.getOID());
            ordinal++;
        }

        String versionName = version.getName() == null || version.getName().trim().isEmpty() ? "v1" : version.getName();
        Map<String, CrfCreateResponse> formResults = new HashMap<String, CrfCreateResponse>();
        for (ODMcomplexTypeDefinitionFormDef formDef : version.getFormDef()) {
            CrfCreateRequest request = buildRequest(formDef, versionName, itemGroups, items, codeLists);
            CrfCreateResponse result = crfMetadataCreationService.createCrf(studyId, request, owner);
            if (CrfCreateResponse.VALIDATION_FAILED.equals(result.getMessage())) {
                response.setMessage(OdmImportResponse.VALIDATION_FAILED);
                response.getErrors().addAll(result.getErrors());
                return response;
            }
            formResults.put(formDef.getOID(), result);
            response.getCrfOids().add(result.getCrfOid());
        }

        for (ODMcomplexTypeDefinitionStudyEventDef eventDef : version.getStudyEventDef()) {
            for (ODMcomplexTypeDefinitionFormRef formRef : eventDef.getFormRef()) {
                CrfCreateResponse result = formResults.get(formRef.getFormOID());
                if (result != null) {
                    crfMetadataCreationService.designate(eventDef.getOID(), result.getCrfOid(), result.getCrfVersionOid(), studyId, owner);
                }
            }
        }

        response.setMessage(OdmImportResponse.SUCCESS);
        return response;
    }

    private void createEvent(ODMcomplexTypeDefinitionStudyEventDef eventDef, Study study, UserAccount user, UserAccountBean owner, int ordinal) {
        StudyEventDefinition existing = studyEventDefinitionDao.findByOcOID(eventDef.getOID());
        if (existing != null) {
            return;
        }
        StudyEventDefinition sed = new StudyEventDefinition();
        sed.setOc_oid(eventDef.getOID());
        sed.setName(eventDef.getName() != null ? eventDef.getName() : eventDef.getOID());
        sed.setDescription(firstText(eventDef.getDescription()));
        sed.setStudy(study);
        sed.setUserAccount(user);
        sed.setStatus(Status.AVAILABLE);
        sed.setType(eventDef.getType() != null ? eventDef.getType().value().toLowerCase() : "scheduled");
        sed.setCategory(eventDef.getCategory() != null ? eventDef.getCategory() : "");
        sed.setRepeating(eventDef.getRepeating() == YesOrNo.YES);
        sed.setOrdinal(ordinal);
        sed.setUpdateId(owner.getId());
        sed.setDateUpdated(new Date());
        Integer id = (Integer) studyEventDefinitionDao.save(sed);
        sed.setStudyEventDefinitionId(id);
    }

    private CrfCreateRequest buildRequest(ODMcomplexTypeDefinitionFormDef formDef, String versionName,
            Map<String, ODMcomplexTypeDefinitionItemGroupDef> itemGroups, Map<String, ODMcomplexTypeDefinitionItemDef> items,
            Map<String, ODMcomplexTypeDefinitionCodeList> codeLists) {
        CrfCreateRequest request = new CrfCreateRequest();
        request.setCrfName(formDef.getName() != null ? formDef.getName() : formDef.getOID());
        request.setDescription(firstText(formDef.getDescription()));
        request.setVersionName(versionName);
        List<CrfCreateRequest.Group> groups = new ArrayList<CrfCreateRequest.Group>();
        List<CrfCreateRequest.Item> requestItems = new ArrayList<CrfCreateRequest.Item>();
        for (ODMcomplexTypeDefinitionItemGroupRef groupRef : formDef.getItemGroupRef()) {
            ODMcomplexTypeDefinitionItemGroupDef groupDef = itemGroups.get(groupRef.getItemGroupOID());
            if (groupDef == null) {
                continue;
            }
            CrfCreateRequest.Group group = new CrfCreateRequest.Group();
            group.setName(groupDef.getName() != null ? groupDef.getName() : groupDef.getOID());
            group.setRepeating(groupDef.getRepeating() == YesOrNo.YES);
            groups.add(group);
            for (ODMcomplexTypeDefinitionItemRef itemRef : groupDef.getItemRef()) {
                ODMcomplexTypeDefinitionItemDef itemDef = items.get(itemRef.getItemOID());
                if (itemDef == null) {
                    continue;
                }
                CrfCreateRequest.Item item = new CrfCreateRequest.Item();
                item.setName(itemDef.getName() != null ? itemDef.getName() : itemDef.getOID());
                item.setLabel(firstText(itemDef.getDescription()));
                item.setGroup(group.getName());
                item.setRequired(itemRef.getMandatory() == YesOrNo.YES);
                if (itemDef.getCodeListRef() != null) {
                    item.setType("radio");
                    ODMcomplexTypeDefinitionCodeList codeList = codeLists.get(itemDef.getCodeListRef().getCodeListOID());
                    if (codeList != null) {
                        List<CrfCreateRequest.Choice> choices = new ArrayList<CrfCreateRequest.Choice>();
                        for (ODMcomplexTypeDefinitionCodeListItem codeListItem : codeList.getCodeListItem()) {
                            CrfCreateRequest.Choice choice = new CrfCreateRequest.Choice();
                            choice.setCode(codeListItem.getCodedValue());
                            choice.setLabel(codeListItem.getDecode() != null ? firstText(codeListItem.getDecode()) : "");
                            choices.add(choice);
                        }
                        item.setChoices(choices);
                    }
                } else {
                    item.setType("text");
                }
                requestItems.add(item);
            }
        }
        request.setGroups(groups);
        request.setItems(requestItems);
        return request;
    }

    private String firstText(ODMcomplexTypeDefinitionDescription description) {
        if (description == null || description.getTranslatedText() == null || description.getTranslatedText().isEmpty()) {
            return "";
        }
        ODMcomplexTypeDefinitionTranslatedText text = description.getTranslatedText().get(0);
        return text.getValue() == null ? "" : text.getValue();
    }

    private String firstText(org.cdisc.ns.odm.v130.ODMcomplexTypeDefinitionDecode decode) {
        if (decode == null || decode.getTranslatedText() == null || decode.getTranslatedText().isEmpty()) {
            return "";
        }
        ODMcomplexTypeDefinitionTranslatedText text = decode.getTranslatedText().get(0);
        return text.getValue() == null ? "" : text.getValue();
    }
}
