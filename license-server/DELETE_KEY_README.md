# Qelvoriflix Key API — exclusão de Keys

Versão preparada, ainda não publicada no servidor ativo.

Nova rota: `DELETE /api/admin/keys/{id}` com `Authorization: Bearer <token>`.

A exclusão é definitiva e invalida imediatamente a licença no servidor. O aplicativo cliente detecta na próxima verificação. Para suspensão reversível, use revogação.

**Importante:** o Render gratuito usa armazenamento efêmero, e novos deploys podem apagar as Keys existentes no SQLite. **Não troque a branch do serviço em produção** até migrar os dados para armazenamento persistente e conferir um backup. Esta branch é isolada para que nenhuma mudança no backend atual seja aplicada automaticamente.
