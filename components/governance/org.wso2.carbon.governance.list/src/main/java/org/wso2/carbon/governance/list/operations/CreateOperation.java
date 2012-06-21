/*
 * Copyright (c) 2012, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.wso2.carbon.governance.list.operations;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.util.AXIOMUtil;
import org.apache.axiom.om.xpath.AXIOMXPath;
import org.apache.axis2.AxisFault;
import org.apache.axis2.context.MessageContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jaxen.JaxenException;
import org.wso2.carbon.governance.api.generic.GenericArtifactManager;
import org.wso2.carbon.governance.api.generic.dataobjects.GenericArtifact;
import org.wso2.carbon.governance.list.operations.util.OperationUtil;
import org.wso2.carbon.governance.list.operations.util.OperationsConstants;
import org.wso2.carbon.registry.core.Registry;
import org.wso2.carbon.registry.core.exceptions.RegistryException;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import java.util.List;

public class CreateOperation extends AbstractOperation {
    private Log log = LogFactory.getLog(CreateOperation.class);
    private String artifactId;

    public CreateOperation(QName name, Registry governanceSystemRegistry, String mediatype, String namespace) {
        super(name, governanceSystemRegistry, mediatype, namespace);
    }

    @Override
    public void setPayload(OMElement bodyContent, String namespace) throws XMLStreamException {
        bodyContent.addChild(AXIOMUtil.stringToOM("<return>" + artifactId + "</return>"));
    }

    @Override
    public String getRequestName() {
        return "info";
    }

    @Override
    public String getRequestType() {
        return "xs:string";
    }

    @Override
    public String getResponseType() {
        return "string";
    }

    public MessageContext process(MessageContext requestMessageContext) throws AxisFault {
        OMElement content = null;
        AXIOMXPath expression;
        try {
            String operation = requestMessageContext.getOperationContext().getAxisOperation().getName().getLocalPart();
            expression = new AXIOMXPath("//ns1:" + operation + "/ns1:info/ns2:metadata");
            expression.addNamespace("ns1", namespace);
            expression.addNamespace("ns2", OperationsConstants.METADATA_NAMESPACE);


            List elements = expression.selectNodes(requestMessageContext.getEnvelope().getBody());
            if(elements.isEmpty()){
                String msg = "Content of the resource should be in correct format";
                log.error(msg);
                OperationUtil.handleException(msg);
            }
            content = (OMElement) elements.get(0);
        } catch (JaxenException e) {
            String msg = "Error occured while reading the content of the SOAP message";
            log.error(msg);
            OperationUtil.handleException(msg, e);
        }

        try {
            GenericArtifactManager artifactManager = new GenericArtifactManager(governanceSystemRegistry, rxtKey);
            GenericArtifact artifact = artifactManager.newGovernanceArtifact(content);
            artifactManager.addGenericArtifact(artifact);
            artifactId = artifact.getId();
        } catch (RegistryException e) {
            String msg = "Error occured while adding the content " + content;
            log.error(msg);
            OperationUtil.handleException(msg, e);
        }

        return getAbstractResponseMessageContext(requestMessageContext);
    }
}
