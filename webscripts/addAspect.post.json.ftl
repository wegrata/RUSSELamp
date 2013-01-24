<#escape x as jsonUtils.encodeJSONString(x)>
{
   "id": "${node.storeType}://${node.storeId}/${node.id}",
   "aspects": "${aspects}"
}
</#escape>