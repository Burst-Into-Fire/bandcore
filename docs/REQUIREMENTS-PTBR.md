# Requisitos

[English](REQUIREMENTS.md) | [Português (Brasil)](REQUIREMENTS-PTBR.md)

Lista completa dos requisitos funcionais (RF), requisitos não funcionais (RNF) e regras de negócio (RN) do **bandcore**. O README mostra uma versão compacta desta lista.

**Legenda de prioridade:** **MVP** é o que a primeira versão precisa. **Em seguida** vem logo depois do MVP. **Depois** está planejado, mas sem data.

**Escopo:** gestão de contas (gastos, divisão e acerto) e merch (catálogo, estoque e vendas). Todo valor monetário é tratado em real (BRL) e dólar (USD).

## Requisitos funcionais

### E1: Acesso e banda

| ID | Requisito | Prioridade | Descrição |
|:---|:---|:---:|:---|
| RF01 | **Cadastro e login** | MVP | Usuários se cadastram e entram com e-mail e senha. A senha é armazenada com hash e a sessão usa tokens JWT de acesso e de renovação. |
| RF02 | **Recuperação de senha** | Em seguida | Quem esquecer a senha a redefine por um link com prazo de validade enviado por e-mail. |
| RF03 | **Criar banda** | MVP | Um usuário logado cria uma banda e vira o admin dela. A banda é o tenant que isola todos os seus dados. |
| RF04 | **Convidar integrantes** | MVP | Admins convidam pessoas por e-mail com um link de aceite. Os convites expiram e podem ser revogados. |
| RF05 | **Perfis por banda** | MVP | Cada integrante tem um perfil na banda: admin (gerencia tudo), integrante (lança e consulta) ou somente leitura. |
| RF06 | **Várias bandas** | Em seguida | Um usuário pode participar de mais de uma banda e alternar entre elas. |
| RF07 | **Desativar integrante** | Depois | Admins desativam um integrante sem perder o histórico ligado a ele. |

### E2: Gastos

| ID | Requisito | Prioridade | Descrição |
|:---|:---|:---:|:---|
| RF08 | **Cadastrar gasto** | MVP | Registrar um gasto com descrição, categoria, valor, moeda (BRL ou USD), data, status (previsto ou pago) e quem pagou. |
| RF09 | **Gastos com data futura** | MVP | Gastos podem ter data futura, para a banda projetar os próximos custos. |
| RF10 | **Categorias padrão** | MVP | O sistema traz categorias padrão, como ensaio, equipamento, vídeo, fotografia, viagem e imprensa. |
| RF11 | **Categorias personalizadas** | Em seguida | A banda cria, renomeia e arquiva as próprias categorias. |
| RF12 | **Editar, cancelar e pagar gastos** | MVP | Gastos podem ser editados, cancelados e marcados como pagos. As alterações mantêm o histórico e nada é apagado de verdade. |
| RF13 | **Anexar comprovante** | Em seguida | Anexar um comprovante (imagem ou PDF) a um gasto. |
| RF14 | **Listar e filtrar gastos** | MVP | Listar gastos filtrando por período, categoria, status, moeda e quem pagou, com paginação. |

### E3: Divisão e acerto

| ID | Requisito | Prioridade | Descrição |
|:---|:---|:---:|:---|
| RF15 | **Divisão igual** | MVP | Dividir um gasto igualmente entre os integrantes escolhidos. |
| RF16 | **Divisão personalizada** | MVP | Dividir por valores personalizados. As cotas precisam somar o total. |
| RF17 | **Saldo por integrante** | MVP | Mostrar o saldo de cada integrante por moeda: quanto deve e quanto tem a receber. |
| RF18 | **Pagamentos de acerto** | MVP | Registrar um pagamento entre integrantes, em uma única moeda, para reduzir os saldos. |
| RF19 | **Acerto sugerido** | Em seguida | Sugerir o menor conjunto de transferências que zera todos os saldos. |
| RF20 | **Receitas manuais** | Em seguida | Lançar receitas, como cachês e aportes dos integrantes, no caixa da banda. |

### E4: Merch: catálogo e estoque

| ID | Requisito | Prioridade | Descrição |
|:---|:---|:---:|:---|
| RF21 | **Cadastrar produtos** | MVP | Cadastrar produto com nome, preço de venda e custo unitário, em BRL, USD ou ambas. |
| RF22 | **Variações de produto** | MVP | Produtos têm variações (tamanho, cor), cada uma com estoque próprio. |
| RF23 | **Foto do produto** | Em seguida | Anexar foto ao produto. |
| RF24 | **Entrada de estoque** | MVP | Registrar entrada de estoque com quantidade e custo do lote. |
| RF25 | **Ajuste de estoque** | Em seguida | Ajustar o estoque manualmente com um motivo: perda, brinde ou recontagem. |
| RF26 | **Alerta de estoque baixo** | Em seguida | Avisar quando uma variação ficar abaixo do limite definido pela banda. |
| RF27 | **Desativar produto** | Depois | Desativar um produto mantendo intactas as vendas anteriores. |

### E5: Merch: vendas

| ID | Requisito | Prioridade | Descrição |
|:---|:---|:---:|:---|
| RF28 | **Registrar venda** | MVP | Registrar uma venda com itens, quantidades, desconto, moeda, forma de pagamento e evento ou local. |
| RF29 | **Baixa automática de estoque** | MVP | Toda venda baixa o estoque automaticamente e é recusada se o estoque for insuficiente. |
| RF30 | **Estornar venda** | MVP | Cancelar ou estornar uma venda, devolvendo os itens ao estoque e revertendo a receita. |
| RF31 | **Tela de venda rápida** | MVP | Uma tela pensada primeiro para o celular, para vender em poucos toques. |
| RF32 | **Vendas offline** | Em seguida | Registrar vendas sem internet e sincronizar depois, sem duplicar. |
| RF33 | **Fechamento de evento** | Em seguida | Fechar um evento com total vendido, por forma de pagamento e por moeda, e o lucro. |

### E6: Integração entre contas e merch

| ID | Requisito | Prioridade | Descrição |
|:---|:---|:---:|:---|
| RF34 | **Custo do lote como gasto** | Em seguida | Transformar o custo de um lote de merch em um gasto dividido entre os integrantes. |
| RF35 | **Destino do lucro do merch** | Depois | Enviar o lucro do merch ao caixa da banda ou usá-lo para abater saldos dos integrantes. |

### E7: Relatórios e dados

| ID | Requisito | Prioridade | Descrição |
|:---|:---|:---:|:---|
| RF36 | **Resumo mensal** | MVP | Mostrar por mês os gastos pagos e previstos, as vendas e o lucro, separados por moeda. |
| RF37 | **Relatórios** | Em seguida | Relatórios por categoria, por produto e por período. |
| RF38 | **Exportar CSV** | Em seguida | Exportar gastos, vendas e estoque em CSV. |
| RF39 | **Importar CSV** | Depois | Importar gastos de uma planilha em CSV. |

### E8: Moedas (BRL e USD)

| ID | Requisito | Prioridade | Descrição |
|:---|:---|:---:|:---|
| RF40 | **Moeda padrão da banda** | MVP | Cada banda define uma moeda padrão (BRL ou USD), usada para preencher formulários e formatar totais. |
| RF41 | **Taxa de câmbio manual** | Depois | Registrar uma taxa de câmbio manual para exibir um total consolidado em uma única moeda. |

## Requisitos não funcionais

### Segurança e privacidade

| ID | Requisito | Prioridade | Descrição |
|:---|:---|:---:|:---|
| RNF01 | **Isolamento entre bandas** | MVP | Toda consulta é restrita à banda. Testes automatizados tentam ler dados de outra banda e precisam falhar. |
| RNF02 | **Autenticação** | MVP | Senhas com hash Argon2 ou bcrypt. Tokens JWT de acesso com expiração curta e tokens de renovação. |
| RNF03 | **Autorização** | MVP | As verificações de perfil são sempre aplicadas no backend, nunca só na interface. |
| RNF04 | **Transporte e segredos** | MVP | Somente HTTPS. Segredos ficam em variáveis de ambiente, nunca no repositório. |
| RNF05 | **Limite de tentativas de login** | MVP | Limitar tentativas de login com falha por conta e por IP. |
| RNF06 | **LGPD** | Em seguida | Termos de uso, política de privacidade, exclusão de conta e exportação de dados sob pedido. |
| RNF07 | **Row Level Security** | Depois | RLS do PostgreSQL como segunda barreira, atrás do isolamento feito na aplicação. |

### Integridade dos dados

| ID | Requisito | Prioridade | Descrição |
|:---|:---|:---:|:---|
| RNF08 | **Representação de dinheiro** | MVP | Valores armazenados como inteiros na menor unidade da moeda, com o código ISO 4217 (BRL ou USD), nunca em ponto flutuante. |
| RNF09 | **Venda transacional** | MVP | Uma venda e a baixa de estoque acontecem na mesma transação. |
| RNF10 | **Estoque não negativo** | MVP | Uma constraint no banco impede estoque negativo. |
| RNF11 | **Trilha de auditoria** | Em seguida | Registrar quem criou, alterou ou cancelou gastos e vendas, e quando. |
| RNF12 | **Exclusão lógica** | Em seguida | Gastos e vendas são excluídos logicamente, nunca fisicamente. |
| RNF13 | **Sincronização idempotente** | Em seguida | Vendas offline carregam uma chave gerada no cliente, para que reenvios nunca criem duplicatas. |

### Usabilidade e plataforma

| ID | Requisito | Prioridade | Descrição |
|:---|:---|:---:|:---|
| RNF14 | **Layout responsivo** | MVP | Utilizável de telas de 360 px até desktop. |
| RNF15 | **PWA instalável** | Em seguida | Instalável no celular, com a casca do app em cache para abrir sem conexão. |
| RNF16 | **Venda rápida** | MVP | Vender um item leva no máximo três toques. |
| RNF17 | **Acessibilidade** | Em seguida | Contraste suficiente, controles com rótulo e alvos de toque de 44 px. |
| RNF18 | **Idioma e fuso horário** | Em seguida | Textos externalizados em pt-BR e inglês. Números, datas e moedas seguem o idioma. Cada banda tem um fuso horário (padrão America/Sao_Paulo). |

### Desempenho e disponibilidade

| ID | Requisito | Prioridade | Descrição |
|:---|:---|:---:|:---|
| RNF19 | **Tempo de resposta** | Em seguida | As telas principais respondem em menos de 2 segundos em 4G. |
| RNF20 | **Paginação** | Em seguida | Listas longas são paginadas. |
| RNF21 | **Disponibilidade** | Em seguida | Meta inicial de disponibilidade de 99%. |
| RNF22 | **Dimensionamento inicial** | Em seguida | Planejar para dezenas de bandas e algumas dezenas de usuários simultâneos, e escolher o plano de hospedagem de acordo. |

### Portabilidade e deploy

| ID | Requisito | Prioridade | Descrição |
|:---|:---|:---:|:---|
| RNF23 | **Containers** | MVP | API e web rodam em containers Docker, e o docker-compose sobe tudo localmente. |
| RNF24 | **PostgreSQL padrão** | MVP | Somente recursos padrão do PostgreSQL, sem extensões específicas de plataforma. |
| RNF25 | **Configuração por ambiente** | MVP | Toda configuração vem de variáveis de ambiente. |
| RNF26 | **CI/CD pelo GitHub** | MVP | Um push na main roda os testes, gera o build e publica. Os workflows filtram por pasta (api, web). |
| RNF27 | **Ambientes separados** | Em seguida | Desenvolvimento e produção são isolados. |
| RNF28 | **Migrações versionadas** | MVP | Mudanças no banco são migrações Flyway aplicadas na subida. |
| RNF29 | **Backups fora da plataforma** | MVP | Backup diário do banco guardado fora da plataforma de hospedagem, com restauração testada. |

### Manutenção e operação

| ID | Requisito | Prioridade | Descrição |
|:---|:---|:---:|:---|
| RNF30 | **Documentação da API** | Em seguida | API REST documentada com OpenAPI. |
| RNF31 | **Testes nas regras críticas** | MVP | Testes automatizados cobrem divisão, saldos, estoque, estornos, isolamento entre bandas e tratamento de moedas. |
| RNF32 | **Logs e monitoramento de erros** | Em seguida | Logs estruturados e monitoramento de erros. |
| RNF33 | **Health check** | Em seguida | Um endpoint de saúde para monitoramento de disponibilidade. |
| RNF34 | **Teto de custo** | Em seguida | Definir um teto de custo mensal e acompanhar o consumo. |

## Regras de negócio

Essas regras costumam virar critérios de aceite das user stories.

| ID | Regra |
|:---|:---|
| RN01 | As cotas de um gasto sempre somam o valor total. |
| RN02 | Saldo do integrante = valor que ele pagou menos as cotas dele, ajustado pelos pagamentos de acerto, calculado por moeda. |
| RN03 | O estoque de uma variação nunca fica abaixo de zero. |
| RN04 | Estornar uma venda devolve o estoque e reverte a receita. |
| RN05 | A venda guarda o preço e o custo vigentes no momento, para que o lucro histórico nunca mude. |
| RN06 | Somente admins alteram perfis ou removem integrantes. |
| RN07 | Alterar o valor de um gasto pago exige registro de auditoria. |
| RN08 | Valores de moedas diferentes nunca são somados. Totais e saldos são mostrados por moeda, a menos que uma taxa de câmbio manual seja registrada. |