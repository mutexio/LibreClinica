/*
 * LibreClinica is distributed under the
 * GNU Lesser General Public License (GNU LGPL).

 * For details see: https://libreclinica.org/license
 * copyright (C) 2003 - 2011 Akaza Research
 * copyright (C) 2003 - 2019 OpenClinica
 * copyright (C) 2020 - 2026 LibreClinica
 */
package org.akaza.openclinica.service.crfdata;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import org.akaza.openclinica.domain.Status;
import org.akaza.openclinica.domain.datamap.CrfBean;
import org.akaza.openclinica.domain.datamap.CrfVersion;
import org.akaza.openclinica.domain.datamap.EventDefinitionCrf;
import org.akaza.openclinica.domain.datamap.Item;
import org.akaza.openclinica.domain.datamap.ItemDataType;
import org.akaza.openclinica.domain.datamap.ItemFormMetadata;
import org.akaza.openclinica.domain.datamap.ItemGroup;
import org.akaza.openclinica.domain.datamap.ItemGroupMetadata;
import org.akaza.openclinica.domain.datamap.ResponseSet;
import org.akaza.openclinica.domain.datamap.ResponseType;
import org.akaza.openclinica.domain.datamap.Section;
import org.akaza.openclinica.domain.datamap.Study;
import org.akaza.openclinica.domain.datamap.StudyEventDefinition;
import org.akaza.openclinica.domain.datamap.VersioningMap;
import org.akaza.openclinica.domain.datamap.VersioningMapId;
import org.akaza.openclinica.domain.user.UserAccount;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CrfMetadataCreationService {

    @Autowired
    private CrfDao crfDao;
    @Autowired
    private CrfVersionDao crfVersionDao;
    @Autowired
    private SectionDao sectionDao;
    @Autowired
    private ItemGroupDao itemGroupDao;
    @Autowired
    private ItemDao itemDao;
    @Autowired
    private ItemFormMetadataDao itemFormMetadataDao;
    @Autowired
    private ItemGroupMetadataDao itemGroupMetadataDao;
    @Autowired
    private ResponseSetDao responseSetDao;
    @Autowired
    private VersioningMapDao versioningMapDao;
    @Autowired
    private ItemDataTypeDao itemDataTypeDao;
    @Autowired
    private ItemReferenceTypeDao itemRefTypeDao;
    @Autowired
    private ResponseTypeDao responseTypeDao;
    @Autowired
    private UserAccountDao userDao;
    @Autowired
    private StudyDao studyDao;
    @Autowired
    private EventDefinitionCrfDao eventDefinitionCrfDao;
    @Autowired
    private StudyEventDefinitionDao studyEventDefinitionDao;

    @Transactional
    public CrfCreateResponse createCrf(int studyId, CrfCreateRequest request, UserAccountBean owner) {
        CrfCreateResponse response = new CrfCreateResponse();
        List<String> errors = validate(request);
        if (!errors.isEmpty()) {
            response.setMessage(CrfCreateResponse.VALIDATION_FAILED);
            response.setErrors(errors);
            return response;
        }

        UserAccount user = userDao.findById(owner.getId());
        Study study = studyDao.findById(studyId);
        Date now = new Date();

        String crfOid = oid("F_", request.getCrfName());
        CrfBean crf = crfDao.findByOcOID(crfOid);
        if (crf == null) {
            crf = new CrfBean();
            crf.setName(request.getCrfName());
            crf.setDescription(nullToEmpty(request.getDescription()));
            crf.setUserAccount(user);
            crf.setStatus(Status.AVAILABLE);
            crf.setStudy(study);
            crf.setOcOid(crfOid);
            crf.setUpdateId(owner.getId());
            crf.setDateUpdated(now);
            Integer crfId = (Integer) crfDao.save(crf);
            crf.setCrfId(crfId);
        }

        String versionName = nullToEmpty(request.getVersionName()).trim();
        if (versionName.isEmpty()) {
            versionName = "v1";
        }
        String versionOid = oid(crfOid + "_", versionName);
        CrfVersion version = crfVersionDao.findByOcOID(versionOid);
        if (version == null) {
            version = new CrfVersion();
            version.setName(versionName);
            version.setDescription(nullToEmpty(request.getDescription()));
            version.setCrf(crf);
            version.setUserAccount(user);
            version.setStatus(Status.AVAILABLE);
            version.setRevisionNotes(nullToEmpty(request.getRevisionNotes()));
            version.setOcOid(versionOid);
            version.setUpdateId(owner.getId());
            version.setDateUpdated(now);
            Integer versionId = (Integer) crfVersionDao.save(version);
            version.setCrfVersionId(versionId);
        }

        Section section = sectionDao.findByCrfVersionOrdinal(version.getCrfVersionId(), 1);
        if (section == null) {
            section = new Section();
            section.setCrfVersion(version);
            section.setStatus(Status.AVAILABLE);
            section.setLabel("");
            section.setTitle("");
            section.setSubtitle("");
            section.setPageNumberLabel("");
            section.setOrdinal(1);
            section.setUserAccount(user);
            section.setBorders(0);
            sectionDao.saveOrUpdate(section);
            section = sectionDao.findByCrfVersionOrdinal(version.getCrfVersionId(), 1);
        }

        List<CrfCreateRequest.Group> groups = request.getGroups();
        String defaultGroupName = request.getCrfName();
        if (groups == null || groups.isEmpty()) {
            CrfCreateRequest.Group fallback = new CrfCreateRequest.Group();
            fallback.setName(defaultGroupName);
            groups = new ArrayList<CrfCreateRequest.Group>();
            groups.add(fallback);
        } else {
            defaultGroupName = groups.get(0).getName();
        }
        Map<String, ItemGroup> groupMap = new LinkedHashMap<String, ItemGroup>();
        Map<String, Boolean> groupRepeating = new LinkedHashMap<String, Boolean>();
        for (CrfCreateRequest.Group groupReq : groups) {
            String groupOid = oid("IG_", groupReq.getName());
            ItemGroup itemGroup = itemGroupDao.findByOcOID(groupOid);
            if (itemGroup == null) {
                itemGroup = new ItemGroup();
                itemGroup.setName(groupReq.getName());
                itemGroup.setCrf(crf);
                itemGroup.setStatus(Status.AVAILABLE);
                itemGroup.setUserAccount(user);
                itemGroup.setOcOid(groupOid);
                itemGroup.setUpdateId(owner.getId());
                itemGroup.setDateUpdated(now);
                Integer itemGroupId = (Integer) itemGroupDao.save(itemGroup);
                itemGroup.setItemGroupId(itemGroupId);
            }
            groupMap.put(groupReq.getName(), itemGroup);
            groupRepeating.put(groupReq.getName(), groupReq.isRepeating());
        }

        int ordinal = 1;
        for (CrfCreateRequest.Item itemReq : request.getItems()) {
            String itemOid = oid("I_", itemReq.getName());
            Item item = itemDao.findByOcOID(itemOid);
            if (item == null) {
                item = new Item();
                item.setName(itemReq.getName());
                item.setDescription("");
                item.setUnits("");
                item.setPhiStatus(false);
                item.setItemDataType(resolveDataType(itemReq.getType()));
                item.setItemReferenceType(itemRefTypeDao.findByItemReferenceTypeId(1));
                item.setStatus(Status.AVAILABLE);
                item.setUserAccount(user);
                item.setOcOid(itemOid);
                item.setUpdateId(owner.getId());
                item.setDateUpdated(now);
                Integer itemId = (Integer) itemDao.save(item);
                item.setItemId(itemId);
            }

            ResponseType responseType = resolveResponseType(itemReq.getType());
            ResponseSet responseSet = resolveResponseSet(itemReq, version, responseType);

            String groupName = nullToEmpty(itemReq.getGroup()).trim();
            if (groupName.isEmpty() || !groupMap.containsKey(groupName)) {
                groupName = defaultGroupName;
            }
            ItemGroup itemGroup = groupMap.get(groupName);
            boolean repeating = Boolean.TRUE.equals(groupRepeating.get(groupName));

            saveItemFormMetadata(item, responseSet, section, version, itemReq, ordinal);
            saveVersioningMap(version, item);
            saveItemGroupMetadata(item, version, itemGroup, repeating, ordinal);
            response.getItemOids().add(item.getOcOid());
            ordinal++;
        }

        if (!nullToEmpty(request.getEventOid()).trim().isEmpty()) {
            designateEvent(request.getEventOid().trim(), crf, version, study, user, owner, now);
        }

        response.setMessage(CrfCreateResponse.SUCCESS);
        response.setCrfOid(crf.getOcOid());
        response.setCrfVersionOid(version.getOcOid());
        return response;
    }

    public void designate(String eventOid, String crfOid, String versionOid, int studyId, UserAccountBean owner) {
        CrfBean crf = crfDao.findByOcOID(crfOid);
        CrfVersion version = crfVersionDao.findByOcOID(versionOid);
        if (crf == null || version == null) {
            return;
        }
        Study study = studyDao.findById(studyId);
        UserAccount user = userDao.findById(owner.getId());
        designateEvent(eventOid, crf, version, study, user, owner, new Date());
    }

    private void designateEvent(String eventOid, CrfBean crf, CrfVersion version, Study study, UserAccount user, UserAccountBean owner, Date now) {
        StudyEventDefinition sed = studyEventDefinitionDao.findByOcOID(eventOid);
        if (sed == null) {
            return;
        }
        List<EventDefinitionCrf> existing = eventDefinitionCrfDao.findByStudyEventDefinitionId(sed.getStudyEventDefinitionId());
        for (EventDefinitionCrf edc : existing) {
            if (edc.getCrf() != null && edc.getCrf().getCrfId() == crf.getCrfId()) {
                return;
            }
        }
        EventDefinitionCrf edc = new EventDefinitionCrf();
        edc.setStudyEventDefinition(sed);
        edc.setStudy(study);
        edc.setCrf(crf);
        edc.setCrfVersion(version);
        edc.setUserAccount(user);
        edc.setStatusId(Status.AVAILABLE.getCode());
        edc.setRequiredCrf(false);
        edc.setDoubleEntry(false);
        edc.setRequireAllTextFilled(false);
        edc.setDecisionConditions(false);
        edc.setNullValues("");
        edc.setOrdinal(existing.size() + 1);
        edc.setElectronicSignature(false);
        edc.setHideCrf(false);
        edc.setSourceDataVerificationCode(3);
        edc.setSelectedVersionIds(String.valueOf(version.getCrfVersionId()));
        edc.setParentId(0);
        edc.setParicipantForm(false);
        edc.setAllowAnonymousSubmission(false);
        edc.setSubmissionUrl("");
        edc.setUpdateId(owner.getId());
        edc.setDateUpdated(now);
        eventDefinitionCrfDao.saveOrUpdate(edc);
    }

    private void saveItemFormMetadata(Item item, ResponseSet responseSet, Section section, CrfVersion version, CrfCreateRequest.Item itemReq, int ordinal) {
        ItemFormMetadata metadata = itemFormMetadataDao.findByItemCrfVersion(item.getItemId(), version.getCrfVersionId());
        if (metadata == null) {
            metadata = new ItemFormMetadata();
            metadata.setItem(item);
            metadata.setCrfVersionId(version.getCrfVersionId());
        }
        metadata.setResponseSet(responseSet);
        metadata.setSection(section);
        metadata.setSubheader("");
        metadata.setHeader(nullToEmpty(itemReq.getSection()));
        metadata.setLeftItemText(nullToEmpty(itemReq.getLabel()));
        metadata.setRightItemText("");
        metadata.setParentId(0);
        metadata.setOrdinal(ordinal);
        metadata.setParentLabel("");
        metadata.setColumnNumber(0);
        metadata.setPageNumberLabel("");
        metadata.setQuestionNumberLabel("");
        metadata.setRegexp("");
        metadata.setRegexpErrorMsg("");
        metadata.setRequired(itemReq.isRequired());
        metadata.setDefaultValue("");
        metadata.setResponseLayout("Vertical");
        metadata.setWidthDecimal("");
        metadata.setShowItem(true);
        itemFormMetadataDao.saveOrUpdate(metadata);
    }

    private void saveVersioningMap(CrfVersion version, Item item) {
        VersioningMapId id = new VersioningMapId();
        id.setCrfVersionId(version.getCrfVersionId());
        id.setItemId(item.getItemId());
        VersioningMap map = new VersioningMap();
        map.setCrfVersion(version);
        map.setItem(item);
        map.setVersionMapId(id);
        versioningMapDao.saveOrUpdate(map);
    }

    private void saveItemGroupMetadata(Item item, CrfVersion version, ItemGroup itemGroup, boolean repeating, int ordinal) {
        ItemGroupMetadata metadata = itemGroupMetadataDao.findByItemCrfVersion(item.getItemId(), version.getCrfVersionId());
        if (metadata == null) {
            metadata = new ItemGroupMetadata();
            metadata.setItem(item);
            metadata.setCrfVersion(version);
        }
        metadata.setItemGroup(itemGroup);
        metadata.setHeader("");
        metadata.setSubheader("");
        metadata.setLayout("");
        metadata.setRepeatingGroup(repeating);
        metadata.setRepeatNumber(1);
        metadata.setRepeatMax(repeating ? 40 : 1);
        metadata.setRepeatArray("");
        metadata.setRowStartNumber(0);
        metadata.setOrdinal(ordinal);
        metadata.setShowGroup(true);
        itemGroupMetadataDao.saveOrUpdate(metadata);
    }

    private ResponseSet resolveResponseSet(CrfCreateRequest.Item itemReq, CrfVersion version, ResponseType responseType) {
        String label = itemReq.getName();
        ResponseSet existing = responseSetDao.findByLabelVersion(label, version.getCrfVersionId());
        if (existing != null) {
            return existing;
        }
        StringBuilder optionsText = new StringBuilder();
        StringBuilder optionsValues = new StringBuilder();
        List<CrfCreateRequest.Choice> choices = itemReq.getChoices();
        if (choices == null || choices.isEmpty()) {
            optionsText.append(responseType.getName());
            optionsValues.append(responseType.getName());
        } else {
            for (CrfCreateRequest.Choice choice : choices) {
                if (optionsText.length() > 0) {
                    optionsText.append(",");
                    optionsValues.append(",");
                }
                optionsText.append(nullToEmpty(choice.getLabel()).replace(",", "\\,"));
                optionsValues.append(nullToEmpty(choice.getCode()));
            }
        }
        ResponseSet responseSet = new ResponseSet();
        responseSet.setLabel(label);
        responseSet.setOptionsText(optionsText.toString());
        responseSet.setOptionsValues(optionsValues.toString());
        responseSet.setResponseType(responseType);
        responseSet.setVersionId(version.getCrfVersionId());
        responseSetDao.saveOrUpdate(responseSet);
        return responseSetDao.findByLabelVersion(label, version.getCrfVersionId());
    }

    private ItemDataType resolveDataType(String type) {
        String normalized = nullToEmpty(type).trim().toLowerCase();
        if (normalized.equals("file")) {
            return itemDataTypeDao.findByItemDataTypeCode("FILE");
        }
        if (normalized.equals("integer") || normalized.equals("int")) {
            return itemDataTypeDao.findByItemDataTypeCode("INT");
        }
        if (normalized.equals("float") || normalized.equals("decimal") || normalized.equals("real")) {
            return itemDataTypeDao.findByItemDataTypeCode("REAL");
        }
        if (normalized.equals("date")) {
            return itemDataTypeDao.findByItemDataTypeCode("DATE");
        }
        return itemDataTypeDao.findByItemDataTypeCode("ST");
    }

    private ResponseType resolveResponseType(String type) {
        String normalized = nullToEmpty(type).trim().toLowerCase();
        if (normalized.equals("checkbox")) {
            return responseTypeDao.findByResponseTypeName("checkbox");
        }
        if (normalized.equals("file")) {
            return responseTypeDao.findByResponseTypeName("file");
        }
        if (normalized.equals("dropdown") || normalized.equals("radio") || normalized.equals("yesno") || normalized.equals("truefalse")) {
            return responseTypeDao.findByResponseTypeName("radio");
        }
        return responseTypeDao.findByResponseTypeName("text");
    }

    private List<String> validate(CrfCreateRequest request) {
        List<String> errors = new ArrayList<String>();
        if (request == null) {
            errors.add("request body is required");
            return errors;
        }
        if (nullToEmpty(request.getCrfName()).trim().isEmpty()) {
            errors.add("crfName is required");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            errors.add("at least one item is required");
        } else {
            for (CrfCreateRequest.Item item : request.getItems()) {
                if (nullToEmpty(item.getName()).trim().isEmpty()) {
                    errors.add("item name is required");
                }
            }
        }
        return errors;
    }

    static String oid(String prefix, String name) {
        String cleaned = nullToEmpty(name).trim().toUpperCase().replaceAll("[^A-Z0-9_]", "_");
        cleaned = cleaned.replaceAll("^_+", "").replaceAll("_+$", "");
        if (cleaned.isEmpty()) {
            cleaned = "UNNAMED";
        }
        String result = prefix + cleaned;
        return result.length() > 40 ? result.substring(0, 40) : result;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
