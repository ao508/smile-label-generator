package org.mskcc.smile.service.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 *
 * @author ochoaa
 */
public class CmoLabelParts {
    private List<String> IGO_SAMPLE_MANIFEST_KEYS = Arrays.asList("igoId", "altId", "cmoSampleClass", "specimenType");
    private List<String> SMILE_SAMPLE_METADATA_KEYS = Arrays.asList("primaryId", "smileSampleId", "smilePatientId", "sampleType", "datasource", "sampleAliases", "patientAliases");
    
    private final ObjectMapper mapper = new ObjectMapper();
    private DataType dataType;
    private String primaryId; // igo => igoId, smile => primaryId
    private String altId; // igo => altId, smile => additionalProperties:altId
    private String cmoPatientId; // smile/igo => cmoPatientId
    private String sampleClass; // igo => specimenType, smile => sampleClass
    private String sampleOrigin; // smile/igo => sampleOrigin
    private String sampleType; // igo => cmoSampleClass, smile => sampleType
    private String detailedSampleType; // smile/igo => cmoSampleIdFields:sampleType
    private String naToExtract; // smile/igo => cmoSampleIdFields:naToExtract
    private String normalizedPatientId; // smile/igo => cmoSampleIdFields:normalizedPatientId
    private String recipe; // smile/igo => cmoSampleIdFields:recipe + smile => genePanel
    private String baitSet; // smile/igo => baitSet
    private String investigatorSampleId; // smile/igo => investigatorSampleId
    private String igoRequestId; // igo => requestId, smile => igoRequestId
    private String origSampleJsonStr;
    
    public enum DataType {
        SAMPLE_METADATA("SampleMetadata"),
        IGO_SAMPLE_MANIFEST("IgoSampleManifest"),
        OTHER("Other");

        private final String value;
        DataType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
    
    public CmoLabelParts(Map<String, Object> sampleMap, String requestId) throws JsonProcessingException {
        // resolve datatype based on map keys
        if (sampleMap.keySet().containsAll(IGO_SAMPLE_MANIFEST_KEYS)) {
            this.dataType = DataType.IGO_SAMPLE_MANIFEST;
        } else if (sampleMap.keySet().containsAll(SMILE_SAMPLE_METADATA_KEYS)) {
            this.dataType = DataType.SAMPLE_METADATA;
        } else {
            this.dataType = DataType.OTHER;
        }
        this.origSampleJsonStr = mapper.writeValueAsString(sampleMap);
        
        if (!dataType.equals(DataType.OTHER)) {
            // fields common to both smile and igo sample data
            this.cmoPatientId = sampleMap.get("cmoPatientId").toString();
            this.sampleOrigin = sampleMap.get("sampleOrigin").toString();
            this.investigatorSampleId = sampleMap.get("investigatorSampleId").toString();
            this.baitSet = sampleMap.get("baitSet").toString();
            
            Map<String, String> cmoSampleIdFields = mapper.convertValue(sampleMap.get("cmoSampleIdFields"), Map.class);
            this.detailedSampleType = cmoSampleIdFields.get("sampleType");
            this.naToExtract = cmoSampleIdFields.get("naToExtract");
            this.normalizedPatientId = cmoSampleIdFields.get("normalizedPatientId");
            this.recipe = cmoSampleIdFields.get("recipe"); // same value as smile => genePanel

            switch (dataType) {
                case DataType.IGO_SAMPLE_MANIFEST:
                    this.primaryId = sampleMap.get("igoId").toString();
                    this.altId = sampleMap.get("altid").toString();
                    this.sampleClass = sampleMap.get("specimenType").toString();
                    this.sampleType = sampleMap.get("cmoSampleClass").toString();
                    this.igoRequestId = requestId;
                default:
                    this.primaryId = sampleMap.get("primaryId").toString();
                    Map<String, String> additionalProperties = mapper.convertValue(sampleMap.get("additionalProperties"), Map.class);
                    this.altId = additionalProperties.get("altId");
                    this.sampleClass = sampleMap.get("sampleClass").toString();
                    this.sampleType = sampleMap.get("sampleType").toString();           
                    this.igoRequestId = additionalProperties.get("igoRequestId");
            }
        }
    }

    /**
     * @return the dataType
     */
    public DataType getDataType() {
        return dataType;
    }

    /**
     * @param dataType the dataType to set
     */
    public void setDataType(DataType dataType) {
        this.dataType = dataType;
    }

    /**
     * @return the primaryId
     */
    public String getPrimaryId() {
        return primaryId;
    }

    /**
     * @param primaryId the primaryId to set
     */
    public void setPrimaryId(String primaryId) {
        this.primaryId = primaryId;
    }

    /**
     * @return the altId
     */
    public String getAltId() {
        return altId;
    }

    /**
     * @param altId the altId to set
     */
    public void setAltId(String altId) {
        this.altId = altId;
    }

    /**
     * @return the cmoPatientId
     */
    public String getCmoPatientId() {
        return cmoPatientId;
    }

    /**
     * @param cmoPatientId the cmoPatientId to set
     */
    public void setCmoPatientId(String cmoPatientId) {
        this.cmoPatientId = cmoPatientId;
    }

    /**
     * @return the sampleClass
     */
    public String getSampleClass() {
        return sampleClass;
    }

    /**
     * @param sampleClass the sampleClass to set
     */
    public void setSampleClass(String sampleClass) {
        this.sampleClass = sampleClass;
    }

    /**
     * @return the sampleOrigin
     */
    public String getSampleOrigin() {
        return sampleOrigin;
    }

    /**
     * @param sampleOrigin the sampleOrigin to set
     */
    public void setSampleOrigin(String sampleOrigin) {
        this.sampleOrigin = sampleOrigin;
    }

    /**
     * @return the sampleType
     */
    public String getSampleType() {
        return sampleType;
    }

    /**
     * @param sampleType the sampleType to set
     */
    public void setSampleType(String sampleType) {
        this.sampleType = sampleType;
    }

    /**
     * @return the detailedSampleType
     */
    public String getDetailedSampleType() {
        return detailedSampleType;
    }

    /**
     * @param detailedSampleType the detailedSampleType to set
     */
    public void setDetailedSampleType(String detailedSampleType) {
        this.detailedSampleType = detailedSampleType;
    }

    /**
     * @return the naToExtract
     */
    public String getNaToExtract() {
        return naToExtract;
    }

    /**
     * @param naToExtract the naToExtract to set
     */
    public void setNaToExtract(String naToExtract) {
        this.naToExtract = naToExtract;
    }

    /**
     * @return the normalizedPatientId
     */
    public String getNormalizedPatientId() {
        return normalizedPatientId;
    }

    /**
     * @param normalizedPatientId the normalizedPatientId to set
     */
    public void setNormalizedPatientId(String normalizedPatientId) {
        this.normalizedPatientId = normalizedPatientId;
    }

    /**
     * @return the recipe
     */
    public String getRecipe() {
        return recipe;
    }

    /**
     * @param recipe the recipe to set
     */
    public void setRecipe(String recipe) {
        this.recipe = recipe;
    }

    /**
     * @return the genePanel
     */
    public String getGenePanel() {
        return baitSet;
    }

    /**
     * @param genePanel the genePanel to set
     */
    public void setGenePanel(String genePanel) {
        this.baitSet = genePanel;
    }

    /**
     * @return the investigatorSampleId
     */
    public String getInvestigatorSampleId() {
        return investigatorSampleId;
    }

    /**
     * @param investigatorSampleId the investigatorSampleId to set
     */
    public void setInvestigatorSampleId(String investigatorSampleId) {
        this.investigatorSampleId = investigatorSampleId;
    }

    /**
     * @return the igoRequestId
     */
    public String getIgoRequestId() {
        return igoRequestId;
    }

    /**
     * @param igoRequestId the igoRequestId to set
     */
    public void setIgoRequestId(String igoRequestId) {
        this.igoRequestId = igoRequestId;
    }
    
    /**
     * @return the origSampleJsonStr
     */
    public String getOrigSampleJsonStr() {
        return origSampleJsonStr;
    }

    /**
     * @param origSampleJsonStr the origSampleJsonStr to set
     */
    public void setOrigSampleJsonStr(String origSampleJsonStr) {
        this.origSampleJsonStr = origSampleJsonStr;
    }
}
