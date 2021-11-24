- Install Azure CLI : brew update && brew install azure-cli

We can also use Azure cloud-shell instead of installig cli : https://docs.microsoft.com/en-us/azure/cloud-shell/quickstart

- Azure Login : az login

- Create a resource group (If not created): az group create --name Fin3 --location centralus

- Get subscription-id : az account list --query "[?isDefault]"
- Create Azure Service Principal for RBAC : 

   az ad sp create-for-rbac --name "myApp" --role contributor --scopes /subscriptions/{subscription-id}/resourceGroups/{resource-group} --sdk-auth
  # Replace {subscription-id}, {resource-group} with the subscription, resource group details


 e.g. az ad sp create-for-rbac --name "Fin3SP" --role contributor --scopes /subscriptions/8aba92e0-e4c7-48cd-b2e5-3701d331141e/resourceGroups/Fin3 --sdk-auth

 Json return by this step will be used as AZURE_CREDENTIALS


- Create Registry : az acr create --resource-group Fin3 --name Fin3Registry --sku Basic
- Enable admin : az acr update -n Fin3Registry --admin-enabled true
- Regenerate login credentials for an Azure Container Registry : az acr credential renew --name Fin3Registry --password-name password --resource-group Fin3
- Add secret AZURE_CREDENTIALS in https://github.com/RadialTheory/digital-currency-consortium/settings/secrets/actions/new 
- Add secret REGISTRY_USERNAME,REGISTRY_PASSWORD in https://github.com/RadialTheory/digital-currency-consortium/settings/secrets/actions/new 
- az provider register --namespace Microsoft.ContainerInstance
Ref : https://github.com/marketplace/actions/deploy-to-azure-container-instances#build-and-deploy-a-nodejs-app-to-azure-container-instances
