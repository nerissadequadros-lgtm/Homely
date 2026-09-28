# Operação da VPS

## Serviços próprios do Homely

Nginx ganhou somente um virtual host em `/etc/nginx/sites-available/homely`, porta 8443. A porta 8443/TCP foi liberada no UFW. O serviço existente de Nginx foi validado e recarregado; os demais aplicativos não foram substituídos. Existe um aviso anterior de opções de protocolo na configuração de outro site em 443, sem impedir a validação.

O Node escuta apenas em 127.0.0.1:3187. PostgreSQL continua na interface local. O serviço usa a conta de sistema `homely`, não root.

```sh
systemctl status homely --no-pager
journalctl -u homely -n 100 --no-pager
curl --cacert /etc/homely/ca.crt https://82.29.61.16:8443/health
systemctl list-timers homely-backup.timer homely-cert.timer
```

## HTTPS sem domínio

A CA privada e a chave do servidor ficam em `/etc/homely`. Chaves privadas têm permissão 600. A CA pública está em `android/app/src/main/res/raw/homely_ca.pem` e só é confiada pelo app para 82.29.61.16. Não existe trust-all nem desativação da verificação de hostname.

O certificado do servidor vale um ano. `homely-cert.timer` verifica semanalmente e renova quando faltarem menos de 30 dias, usando a mesma CA; o app não precisa ser atualizado nessa renovação. A CA tem validade de dez anos. Mudança de IP ou CA exige atualização de configuração/certificado no app.

Em clientes Windows com Schannel, a CA privada não publica CRL. Para uma verificação local usando curl, use `--cacert <ca.pem> --ssl-revoke-best-effort`; a cadeia e o IP ainda são verificados. Não use `-k`.

## Atualização da API

Envie somente código/lockfile/assets de implantação. Não copie `.env`, caches ou credenciais do computador.

```sh
cd /opt/homely/server
npm ci --include=dev
npm run build
set -a
source /etc/homely/api.env
set +a
npm run migrate
systemctl restart homely
curl --cacert /etc/homely/ca.crt https://82.29.61.16:8443/health
```

A migração inicial é idempotente. A semente também é idempotente por autor/título. Mudanças futuras de schema devem receber migrações versionadas antes de modificar dados existentes.

## Testes

O banco `homely_test` é independente do banco `homely`. Após carregar o ambiente, defina `TEST_DATABASE_URL` apontando para ele; execute a migração nesse banco e então `npm test`. Nunca rode testes destrutivos apontando para produção. O teste cria e remove somente os próprios usuários.

## Backups

`homely-backup.timer` executa diariamente às 04:30 UTC (com pequeno atraso aleatório). `/opt/homely/backup.sh` salva PostgreSQL em formato custom, mídias e configuração privada em `/var/backups/homely/<dataUTC>`, com acesso restrito. A primeira cópia foi executada durante a implantação.

As cópias estão na mesma máquina: não protegem contra perda da VPS. Configure um destino externo antes de lançamento público. A retenção inicial é manual; acompanhe o espaço em disco para não acumular indefinidamente.

Restauração deve ser feita com o serviço parado e o destino confirmado. Restaure o dump com `pg_restore`, mídias em `/var/lib/homely` e configurações em `/etc/homely`; preserve dono/permissões e valide Nginx antes de ligar o serviço.

## Moderação e lançamento

Denúncias ficam na tabela `reports`; ainda não há painel administrativo. O catálogo aceita conteúdo dos usuários após autenticação. Defina fluxo de revisão, atendimento, política de privacidade e termos antes de abrir cadastro ao público.

O APK entregue é debug. A chave definitiva de assinatura precisa ser criada/guardada pelo proprietário antes de distribuição na loja. E-mail de recuperação/verificação, SDK de anúncios e backup externo dependem dos serviços/contas escolhidos pelo proprietário.
