# Qelvoriflix — Servidor de Keys

API Python FastAPI compatível com o APK administrador Qelvoriflix Key Admin.
Não é o APK Qelvoriflix Pro. A versão do cliente deve consultar
`POST /api/v1/verify` periodicamente para detectar Keys revogadas.

## Deploy no Render
1. Criar **Web Service** apontando para o repositório `qelvori-build`.
2. Selecionar a branch `qelvoriflix-servidor-render-2026`.
3. Em **Root Directory**, informar `license-server`.
4. Runtime: Python. Build: `pip install -r requirements.txt`.
5. Start: `uvicorn main:app --host 0.0.0.0 --port $PORT`.
6. Configurar variáveis no painel da hospedagem (NÃO armazenar neste repo):
   - `ADMIN_USER`: admin
   - `ADMIN_PASSWORD`: senha forte, mínimo 12 caracteres
   - `SESSION_SECRET`: token aleatório, mínimo 32 caracteres
   - `KEY_PEPPER`: segundo token aleatório, mínimo 32 caracteres
   - `DATA_DIR`: /var/data
7. Monte um disco persistente em **/var/data**. O serviço gratuito não possui disco durável: pode perder todas as Keys. Um disco persistente pode exigir plano pago.
8. Acesse `https://seu-servico.onrender.com/health` (deve responder `{"ok":true}`).

## APK administrador
Na tela de login, usar a URL `https://seu-servico.onrender.com`,
o usuário e a senha do ambiente Render.

## Endpoints
- `POST /api/admin/login` com `username`, `password`.
- `GET /api/admin/keys` com Bearer token.
- `POST /api/admin/keys` com `weeks` e `note`.
- `POST /api/admin/keys/{id}/revoke`, `restore`, `reset-device`.
- `POST /api/v1/activate` e `POST /api/v1/verify` com `key`, `device_id`.
- `GET /health`.

O backend registra apenas hashes das Keys. A Key completa é mostrada
uma única vez quando gerada. Preserve `KEY_PEPPER` entre reinicializações.
