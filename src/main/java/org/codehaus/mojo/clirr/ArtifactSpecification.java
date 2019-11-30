package org.codehaus.mojo.clirr;

/*
 * Copyright 2006 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * An artifact specification.
 */
public class ArtifactSpecification
{
    /**
     * The artifacts groupID.
     */
    private String groupId;

    /**
     * The artifacts artifactID.
     */
    private String artifactId;

    /**
     * The artifacts version number.
     */
    private String version;

    /**
     * The artifacts classifier.
     */
    private String classifier;

    /**
     * The artifacts type; defaults to "jar".
     */
    private String type;

    /**
     * Returns the artifacts groupId.
     *
     * @return the group id
     */
    public String getGroupId()
    {
        return groupId;
    }

    /**
     * Sets the artifacts groupId.
     *
     * @param groupId the new group id
     */
    public void setGroupId( String groupId )
    {
        this.groupId = groupId;
    }

    /**
     * Returns the artifacts artifactId.
     *
     * @return the artifact id
     */
    public String getArtifactId()
    {
        return artifactId;
    }

    /**
     * Sets the artifacts artifactId.
     *
     * @param artifactId the new artifact id
     */
    public void setArtifactId( String artifactId )
    {
        this.artifactId = artifactId;
    }

    /**
     * Returns the artifacts version number.
     *
     * @return the version
     */
    public String getVersion()
    {
        return version;
    }

    /**
     * Sets the artifacts version number.
     *
     * @param version the new version
     */
    public void setVersion( String version )
    {
        this.version = version;
    }

    /**
     * Returns the artifacts classifier.
     *
     * @return the classifier
     */
    public String getClassifier()
    {
        return classifier;
    }

    /**
     * Sets the artifacts classifier.
     *
     * @param classifier the new classifier
     */
    public void setClassifier( String classifier )
    {
        this.classifier = classifier;
    }

    /**
     * Returns the artifacts type; defaults to "jar".
     *
     * @return the type
     */
    public String getType()
    {
        return type;
    }

    /**
     * Sets the artifacts type; defaults to "jar".
     *
     * @param type the new type
     */
    public void setType( String type )
    {
        this.type = type;
    }
}
