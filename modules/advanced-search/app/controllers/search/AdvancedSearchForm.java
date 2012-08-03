package controllers.search;

/**
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since Sep 30, 2010 2:23:50 PM
 */

public class AdvancedSearchForm {
    private static final String ALL_COLLECTIONS = "all";
    private String facet0 = "";
    private String value0 = "";
    private String operator1 = "";
    private String facet1 = "";
    private String value1 = "";
    private String operator2 = "";
    private String facet2 = "";
    private String value2 = "";
    private String creationFrom = "";
    private String creationTo = "";
    private String birthFrom = "";
    private String birthTo = "";
    private String acquisitionFrom = "";
    private String acquisitionTo = "";
    private String purchasePrice = "";
    private boolean allProvinces = true;
    private String[] provinceList = null;
    private String allCollections = ALL_COLLECTIONS;
    private String collection = "";
    private String[] collectionList = null;
    private String vervaardigerscollectief = "";
    private String techniek = "";
    private String materiaal = "";
    private String wijze_van_verwerving = "";
    private String verworven_met_steun_van = "";
    private String locatie = "";
    private String deelcollectie = "";
    private String fysieke_staat = "";


    private String sortBy = "";

    public String getFacet0() {
        return facet0;
    }

    public void setFacet0(String facet0) {
        this.facet0 = facet0;
    }

    public String getValue0() {
        return value0;
    }

    public void setValue0(String value0) {
        this.value0 = value0;
    }

    public String getOperator1() {
        return operator1;
    }

    public void setOperator1(String operator1) {
        this.operator1 = operator1;
    }

    public String getFacet1() {
        return facet1;
    }

    public void setFacet1(String facet1) {
        this.facet1 = facet1;
    }

    public String getValue1() {
        return value1;
    }

    public void setValue1(String value1) {
        this.value1 = value1;
    }

    public String getOperator2() {
        return operator2;
    }

    public void setOperator2(String operator2) {
        this.operator2 = operator2;
    }

    public String getFacet2() {
        return facet2;
    }

    public void setFacet2(String facet2) {
        this.facet2 = facet2;
    }

    public String getValue2() {
        return value2;
    }

    public void setValue2(String value2) {
        this.value2 = value2;
    }

    public String getCreationFrom() {
        return creationFrom;
    }

    public void setCreationFrom(String creationFrom) {
        this.creationFrom = creationFrom;
    }

    public String getCreationTo() {
        return creationTo;
    }

    public void setCreationTo(String creationTo) {
        this.creationTo = creationTo;
    }

    public String getBirthFrom() {
        return birthFrom;
    }

    public void setBirthFrom(String birthFrom) {
        this.birthFrom = birthFrom;
    }

    public String getBirthTo() {
        return birthTo;
    }

    public void setBirthTo(String birthTo) {
        this.birthTo = birthTo;
    }

    public String getAcquisitionFrom() {
        return acquisitionFrom;
    }

    public void setAcquisitionFrom(String acquisitionFrom) {
        this.acquisitionFrom = acquisitionFrom;
    }

    public String getAcquisitionTo() {
        return acquisitionTo;
    }

    public void setAcquisitionTo(String acquisitionTo) {
        this.acquisitionTo = acquisitionTo;
    }

    public String getPurchasePrice() {
        return purchasePrice;
    }

    public void setPurchasePrice(String purchasePrice) {
        this.purchasePrice = purchasePrice;
    }

    public boolean isAllProvinces() {
        return allProvinces;
    }

    public void setAllProvinces(boolean allProvinces) {
        this.allProvinces = allProvinces;
    }

    public String[] getProvinceList() {
        return provinceList;
    }

    public void setProvinceList(String[] provinceList) {
        this.provinceList = provinceList;
    }

    public String getAllCollections() {
        return allCollections;
    }

    public void setAllCollections(String allCollections) {
        this.allCollections = allCollections;
    }


    public String getCollection() {
        return collection;
    }

    public void setCollection(String collection) {
        this.collection = collection;
    }

    public String[] getCollectionList() {
        return collectionList;
    }

    public void setCollectionList(String[] collectionList) {
        this.collectionList = collectionList;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public String getVervaardigerscollectief() {
        return vervaardigerscollectief;
    }

    public void setVervaardigerscollectief(String vervaardigerscollectief) {
        this.vervaardigerscollectief = vervaardigerscollectief;
    }

    public String getTechniek() {
        return techniek;
    }

    public void setTechniek(String techniek) {
        this.techniek = techniek;
    }

    public String getMateriaal() {
        return materiaal;
    }

    public void setMateriaal(String materiaal) {
        this.materiaal = materiaal;
    }

    public String getWijze_van_verwerving() {
        return wijze_van_verwerving;
    }

    public void setWijze_van_verwerving(String wijze_van_verwerving) {
        this.wijze_van_verwerving = wijze_van_verwerving;
    }

    public String getVerworven_met_steun_van() {
        return verworven_met_steun_van;
    }

    public void setVerworven_met_steun_van(String verworven_met_steun_van) {
        this.verworven_met_steun_van = verworven_met_steun_van;
    }

    public String getLocatie() {
        return locatie;
    }

    public void setLocatie(String locatie) {
        this.locatie = locatie;
    }

    public String getDeelcollectie() {
        return deelcollectie;
    }

    public void setDeelcollectie(String deelcollectie) {
        this.deelcollectie = deelcollectie;
    }

    public String getFysieke_staat() {
        return fysieke_staat;
    }

    public void setFysieke_staat(String fysieke_staat) {
        this.fysieke_staat = fysieke_staat;
    }

    public String toSolrQuery() {
        StringBuilder builder = new StringBuilder();
        builder.append(makeQueryString(value0, facet0, operator1, value1));
        builder.append(makeQueryString(value1, facet1, operator2, value2));
        builder.append(makeQueryString(value2, facet2, "", ""));
        if (builder.length() == 0) {
            builder.append("*:*");
        }
        if (isValidRangeQuery(creationFrom, creationTo)) {
            builder.append(" ").append(makeRangeQueryString("dc_date", creationFrom, creationTo));
        }
        if (isValidRangeQuery(acquisitionFrom, acquisitionTo)) {
            builder.append(" ").append(makeRangeQueryString("icn_acquisitionYear", acquisitionFrom, acquisitionTo));
        }
        if (isValidRangeQuery(birthFrom, birthTo)) {
            builder.append(" ").append(makeRangeQueryString("icn_creatorYearOfBirth", birthFrom, birthTo));
        }
        if (isValid(purchasePrice)) {
            builder.append(" ").append("icn_purchasePrice:");
            if (purchasePrice.equalsIgnoreCase("100")) {
                builder.append("[* TO 100]");
            }
            else if (purchasePrice.equalsIgnoreCase("1000")) {
                builder.append("[100 TO 1000]");
            }
            else if (purchasePrice.equalsIgnoreCase("10000")) {
                builder.append("[1000 TO 10000]");
            }
            else if (purchasePrice.equalsIgnoreCase("100000")) {
                builder.append("[10000 TO 100000]");
            }
            else if (purchasePrice.equalsIgnoreCase("1000000")) {
                builder.append("[100000 TO 1000000]");
            }
        }
        if (provinceList != null && !allProvinces) {
            for (String prov : provinceList) {
                builder.append("&qf=icn_province:").append(prov);
            }
        }
        if (collectionList != null && !ALL_COLLECTIONS.equals(allCollections)) {
            for (String coll : collectionList) {
                builder.append("&qf=delving_owner_facet:").append(coll);
            }
        }
        if (isValid(collection) && !collection.equalsIgnoreCase("all_collections")) {
            builder.append("&qf=delving_owner_facet:").append(collection).append("");
        }
        if (isValid(getSortBy())) {
            builder.append("&sortBy=").append(getSortBy());
        }
        // todo this doesn't seem to be part of the FullBean model
        if (isValid(vervaardigerscollectief)) {
            builder.append("&qf=text:").append(vervaardigerscollectief);
        }
        if (isValid(techniek)) {
            builder.append("&qf=icn_technique:").append(techniek);
        }
        if (isValid(materiaal)) {
            builder.append("&qf=icn_material:").append(materiaal);
        }
        if (isValid(wijze_van_verwerving)) {
            builder.append("&qf=icn_acquisitionMeans:").append(wijze_van_verwerving);
        }
        if (isValid(verworven_met_steun_van)) {
            builder.append("&qf=icn_acquiredWithHelpFrom:").append(verworven_met_steun_van);
        }
        if (isValid(locatie)) {
            builder.append("&qf=icn_location:").append(locatie);
        }
        if (isValid(deelcollectie)) {
            builder.append("&qf=icn_collectionPart:").append(deelcollectie);
        }
        if (isValid(fysieke_staat)) {
            builder.append("&qf=icn_physicalState:").append(fysieke_staat);
        }
        return builder.toString().trim();
    }

    private String makeRangeQueryString(String fieldName, String from, String to) {
        StringBuilder out = new StringBuilder();
        out.append(fieldName).append(":[");
        if (isValid(from)) {
            out.append(from);
        }
        else {
            out.append("*");
        }
        out.append(" TO ");
        if (isValid(to)) {
            out.append(to);
        }
        else {
            out.append("*");
        }
        out.append("]");
        return out.toString();
    }

    private boolean isValidRangeQuery(String from, String to) {
        if (isValid(from) || isValid(to)) {
            return true;
        }
        return false;
    }

    private String makeQueryString(String value, String facet, String operator, String nextValue) {
        StringBuilder builder = new StringBuilder();
        if (isValid(value)) {
            if (isValid(facet)) {
                builder.append(facet);
            }
            else {
                builder.append("text");
            }
            builder.append(":").append(value);
            if (isValid(operator) && isValid(nextValue)) {
                if (!operator.equalsIgnoreCase("and")) {
                    builder.append(" ").append(operator).append(" ");
                }
                else {
                    builder.append(" ");
                }
            }
        }
        return builder.toString();
    }

    private boolean isValid(String field) {
        boolean valid = false;
        if (field != null && !field.isEmpty()) {
            valid = true;
        }
        return valid;
    }

    @Override
    public String toString() {
        return "AdvancedSearchForm={" +
                "facet0='" + facet0 + '\'' +
                ", value0='" + value0 + '\'' +
                ", operator1='" + operator1 + '\'' +
                ", facet1='" + facet1 + '\'' +
                ", value1='" + value1 + '\'' +
                ", operator2='" + operator2 + '\'' +
                ", facet2='" + facet2 + '\'' +
                ", value2='" + value2 + '\'' +
                ", creationFrom=" + creationFrom +
                ", creationTo=" + creationTo +
                ", birthFrom=" + birthFrom +
                ", birthTo=" + birthTo +
                ", acquisitionFrom=" + acquisitionFrom +
                ", acquisitionTo=" + acquisitionTo +
                ", purchasePrice=" + purchasePrice +
                ", allProvinces=" + allProvinces +
                ", provinceList='" + provinceList + '\'' +
                ", allCollections='" + allCollections + '\'' +
                ", collection='" + collection + '\'' +
                ", sortBy='" + sortBy + '\'' +
                '}';
    }
}