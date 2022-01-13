package samples.testbed;

import com.ib.client.ContractDetails;

public class StockAndOptionContracts {

    private ContractDetails contractDetails;
    private SecurityDefinitionOptionalParameter securityDefinitionOptionalParameter;

    public ContractDetails getContractDetails() {
        return contractDetails;
    }

    public void setContractDetails(ContractDetails contractDetails) {
        this.contractDetails = contractDetails;
    }

    public SecurityDefinitionOptionalParameter getSecurityDefinitionOptionalParameter() {
        return securityDefinitionOptionalParameter;
    }

    public void setSecurityDefinitionOptionalParameter(SecurityDefinitionOptionalParameter securityDefinitionOptionalParameter) {
        this.securityDefinitionOptionalParameter = securityDefinitionOptionalParameter;
    }

    @Override
    public String toString() {
        return "StockAndOptionContracts{" +
                "contractDetails=" + contractDetails +
                ", securityDefinitionOptionalParameter=" + securityDefinitionOptionalParameter +
                '}';
    }
}
