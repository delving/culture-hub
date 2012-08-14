/*
 * Copyright 2011 DELVING BV
 *
 * Licensed under the EUPL, Version 1.1 or as soon they
 * will be approved by the European Commission - subsequent
 * versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the
 * Licence.
 * You may obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
 *
 * Unless required by applicable law or agreed to in
 * writing, software distributed under the Licence is
 * distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied.
 * See the Licence for the specific language governing
 * permissions and limitations under the Licence.
 */

package controllers.search;

import java.util.Arrays;

/**
 * @author Sjoerd Siebinga <sjoerd.siebinga@gmail.com>
 * @since Sep 30, 2010 2:23:50 PM
 */

public class AdvancedSearchForm2 {
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
    private boolean onlyDigitalObjects = false;
    private boolean allCounties = true;
    private String[] countyList = null;
    private boolean allMunicipalities = true;
    private String[] municipalityList = null;
    private boolean allOwners = true;
    private String owner = "";
    private String[] ownerList = null;
    private boolean allProviders = true;
    private String[] providersList = null;
    private boolean allTypes = false;
    private String[] typeList = null;
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

    public boolean isOnlyDigitalObjects() {
        return onlyDigitalObjects;
    }

    public void setOnlyDigitalObjects(boolean onlyDigitalObjects) {
        this.onlyDigitalObjects = onlyDigitalObjects;
    }

    public boolean isAllCounties() {
        return allCounties;
    }

    public void setAllCounties(boolean allCounties) {
        this.allCounties = allCounties;
    }

    public String[] getCountyList() {
        return countyList;
    }

    public void setCountyList(String[] countyList) {
        this.countyList = countyList;
    }

    public boolean isAllMunicipalities() {
        return allMunicipalities;
    }

    public void setAllMunicipalities(boolean allMunicipalities) {
        this.allMunicipalities = allMunicipalities;
    }


    public String[] getMunicipalityList() {
        return municipalityList;
    }

    public void setMunicipalityList(String[] municipalityList) {
        this.municipalityList = municipalityList;
    }

    public void setAllOwners(boolean allOwners) {
        this.allOwners = allOwners;
    }

    public boolean getAllOwners() {
        return allOwners;
    }


    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String[] getOwnerList() {
        return ownerList;
    }

    public void setOwnerList(String[] ownerList) {
        this.ownerList = ownerList;
    }

    public boolean isAllProviders() {
        return allProviders;
    }

    public void setAllProviders(boolean allProviders) {
        this.allProviders = allProviders;
    }

    public String[] getProvidersList() {
        return providersList;
    }

    public void setProvidersList(String[] providersList) {
        this.providersList = providersList;
    }

    public boolean isAllTypes() {
        return allTypes;
    }

    public void setAllTypes(boolean allTypes) {
        this.allTypes = allTypes;
    }

    public String[] getTypeList() {
        return typeList;
    }

    public void setTypeList(String[] typeList) {
        this.typeList = typeList;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public boolean isAllCollections() {
        return allOwners;
    }

    public void setAllCollections(String allCollections) {
        boolean allColl = true;
        if (!allCollections.equalsIgnoreCase("all")) {
            allColl = false;
        }
        this.allOwners = allColl;
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
        if (countyList != null && !allCounties) {
            for (String county : countyList) {
                builder.append("&qf=abm_county_facet:").append(county);
            }
        }
        if (ownerList != null && !allOwners) {
            for (String coll : ownerList) {
                builder.append("&qf=delving_owner_facet:").append(coll);
            }
        }
         if (providersList != null && !allProviders) {
            for (String dp : providersList) {
                builder.append("&qf=delving_provider_facet:").append(dp);
            }
        }
        if (typeList != null) {
            for (String type : typeList) {
                builder.append("&qf=dc_type_facet:").append(type);
            }
        }
        if (municipalityList != null && !allMunicipalities) {
            for (String type : municipalityList) {
                builder.append("&qf=abm_municipality_facet:").append(type);
            }
        }
        if (onlyDigitalObjects) {
            builder.append("&qf=delving_hasDigitalObject_facet:true");
        }
        if (isValid(owner) && !owner.equalsIgnoreCase("all_owners")) {
            builder.append("&qf=delving_owner:").append(owner).append("");
        }
        if (isValid(getSortBy())) {
            builder.append("&sortBy=").append(getSortBy());
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
        return isValid(from) || isValid(to);
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
                ", allCounties=" + allCounties +
                ", countyList='" + Arrays.toString(countyList) + '\'' +
                ", providersList='" + Arrays.toString(providersList) + '\'' +
                ", municipalityList='" + Arrays.toString(municipalityList) + '\'' +
                ", allCollections='" + allOwners + '\'' +
                ", collection='" + owner + '\'' +
                ", sortBy='" + sortBy + '\'' +
                '}';
    }
}