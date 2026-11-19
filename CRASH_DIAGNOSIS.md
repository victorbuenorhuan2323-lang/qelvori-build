# Diagnóstico do fechamento — Qelvoriflix Pro 3.1.2

Data: 19/11/2026

## Resultado da análise estática
O arquivo original `Qelvoriflix_pro_3.1.2.apk` contém:

- `android:appComponentFactory="on.dev.shellv3.ShellAppFactoryV3"`.
- `android:name="com.pairip.application.Application"`.
- `assets/ondev/p`, `lib/arm64-v8a/libondevx.so` e `classes.dex` com o carregador nativo.
- `ShellAppFactoryV3.buildRealLoader` chama `certSetHashFromApk`, envia esse valor à rotina `Nx.decrypt` e constrói um `InMemoryDexClassLoader`.
- Na falha, o carregador produz mensagens como `native decrypt failed` e `OnDev V3: failed to load protected dex`.

O certificado do APK original tem SHA-256:
`e3432e683895de0915dda8a60d6f7aa57541d5dabfb54f8594e47f7f11ce4472`.

A versão modificada e assinada tem SHA-256:
`ac17ede64f6513504e1a5a77d202fc0b9c83501fef033d8d3cdb412850edc937`.

**Hipótese forte (não comprovada sem logcat):** a troca de certificado interfere na descriptografia da carga protegida, causando fechamento na inicialização, antes da exibição da tela de Key.

## O que já foi verificado
- O AndroidManifest modificado está sintaticamente legível pelo `aapt2 dump xmltree`, com launcher `com.th.lisoprime.license.KeyGateActivity`.
- `classes2.dex` contém `KeyGateActivity`.
- As assinaturas v1, v2 e v3 do APK de teste estão íntegras.

## Próximo passo legítimo
Não distribuir outra recompilação apenas com nova assinatura. Obter **código-fonte Android** e a chave original de assinatura com autorização do proprietário do aplicativo; integrar o fluxo de licença e reconstruir, mantendo a compatibilidade com o sistema de proteção. Se não houver fontes, consultar o mantenedor da proteção para um mecanismo suportado de extensão/rebuild.

Para confirmar o ponto exato da falha, capturar e inspecionar o logcat no dispositivo, por exemplo:

```sh
su -c 'logcat -b crash -d -v time | tail -n 100'
```

## Outra pendência independente
O endpoint HTTPS da Central de Keys respondeu HTTP 401 à consulta anônima `/api/license/status`, portanto o servidor de licença também necessita integração apropriada.
