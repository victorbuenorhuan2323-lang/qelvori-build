# Qelvoriflix Pro 3.1.2 — KeyGate (experimental)

Projeto Android + utilitário de patch para inserir uma tela de ativação por Key no APK original.

## Recursos implementados na tela de entrada
- Chaves de 14, 28 e 56 dias validadas pela API.
- Armazenamento do identificador aleatório do dispositivo e da Key para verificações posteriores.
- Bloqueio da abertura da Activity principal sem resposta de ativação válida.
- Consulta a `POST /api/license/activate` e `POST /api/license/status`.
- Tempo restante calculado a partir de `expires_at`.

## GitHub Actions
- `Compile Qelvoriflix KeyGate` compila a tela Android e publica as classes DEX.
- `Patch Qelvoriflix Pro APK (experimental)` exige o arquivo `input/Qelvoriflix_pro_3.1.2.apk` **e uma URL HTTPS que exponha a API**. O workflow injeta a Activity no manifesto binário, inclui o DEX, alinha e assina um APK novo.

## Limitações importantes
O original usa proteção nativa (`assets/ondev/p` e `libondevx.so`). A recompilação altera a assinatura, e **o app pode recusar a assinatura ou falhar durante a execução**. O APK original não é alterado; para instalar a versão experimental pode ser necessário remover a anterior, portanto faça backup.

O bloqueio no código experimental ocorre ao abrir novamente o aplicativo; **a implementação ainda não monitora expiração em tempo real durante a reprodução**. Para autorização robusta durante toda a sessão, integre verificações no código-fonte original.

A Central de Keys deve estar hospedada em HTTPS e oferecer endpoints JSON compatíveis. Um site estático sozinho não valida Keys. Não distribua o APK modificado sem os direitos necessários sobre o aplicativo.
