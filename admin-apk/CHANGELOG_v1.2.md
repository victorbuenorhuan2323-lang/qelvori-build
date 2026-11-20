# v1.2
- Novo ícone Q com coroa azul e emblema de reprodução.
- Marca visível no cabeçalho.
- Excluir Key definitivamente com confirmação.
- Contador vivo de dias, horas, minutos e segundos preservado.

Backend necessário: DELETE /api/admin/keys/{id} com autenticação Bearer.
A versão de backend que contém essa rota está isolada na branch qelvoriflix-servidor-exclusao-2026 para não apagar Keys no Render Free durante um redeploy automático.
