# Qelvoriflix Pro — Android build

Repositório reservado para a integração do sistema de Keys ao aplicativo Qelvoriflix Pro.

## Licenças
- 2 semanas: 14 dias
- 4 semanas: 28 dias
- 8 semanas: 56 dias

A aplicação deverá chamar os endpoints do backend `POST /api/license/activate` e `POST /api/license/status`, exibir dias, horas e minutos restantes e impedir o acesso após o vencimento. A validação precisa ocorrer no servidor.

## Compilação
O workflow `.github/workflows/android.yml` gera um APK debug quando o projeto Android completo estiver em `android/` (incluindo `gradlew`, wrapper e código-fonte).

**Estado atual:** o código-fonte Android ainda não está neste repositório. O APK pré-compilado fornecido separadamente não é um projeto Gradle; o workflow falhará até que o código-fonte seja integrado. Nenhum APK modificado foi gerado.

O backend da Central de Keys também deverá ser hospedado em HTTPS antes do uso real.
