# IPED MCP Server

Servidor MCP (Model Context Protocol) para o [IPED](https://github.com/sepinf-inc/IPED) — ferramenta forense digital da Polícia Federal Brasileira.

Acessa os casos processados do IPED diretamente via Java (PyJnius), sem passar pela API REST.

## Requisitos

- **Python 3.10+**
- **Java JDK 11** com JavaFX (ex: Liberica JDK 11 Full)
- **IPED** instalado e um caso já processado

## Instalação

```bash
cd iped-mcp-server
pip install -r requirements.txt
```

## Configuração

Edite o arquivo `.env` (somente as variáveis opcionais):

```env
# Opcional: caminho do JDK (se diferente do JAVA_HOME do sistema)
# Se não definido, o servidor procura automaticamente: variável de ambiente → pasta "jre" no IPED → PATH do sistema
JAVA_HOME=C:\Program Files\Java\jdk-11

# Memória máxima para a JVM
JVM_MAX_HEAP=4g
```

> **Nota:** `IPED_HOME` e `CASE_PATH` são resolvidos automaticamente a partir da
> localização do arquivo `config.py` (caminhos relativos). Não é necessário
> configurá-los no `.env`.

## Uso

### Iniciar servidor (modo stdio — padrão para OpenCode)

```bash
cd C:\iped\iped-mcp-server
python -m src.main
```

### Iniciar servidor (modo SSE — para testes HTTP)

```bash
cd C:\iped\iped-mcp-server
python -m src.main sse
```

## Ferramentas MCP Disponíveis

| Ferramenta | Descrição |
|------------|-----------|
| `list_sources()` | Lista todas as fontes (casos) abertas |
| `search(query, source_id?)` | Busca com sintaxe Lucene |
| `search_by_type(file_type, source_id?)` | Busca por extensão (pdf, jpg, etc.) |
| `search_by_name(pattern, source_id?)` | Busca por nome com wildcards |
| `get_searchable_fields()` | Retorna nomes e tipos dos campos indexáveis |
| `get_document(source_id, doc_id)` | Metadados do documento |
| `get_document_content(source_id, doc_id)` | Conteúdo binário (base64) |
| `get_document_text(source_id, doc_id)` | Texto extraído pelo parser |
| `read(source_id, doc_id)` | Metadados + texto completos de um documento |
| `read_batch(source_id, doc_ids)` | Metadados + texto de múltiplos documentos |
| `list_bookmarks()` | Lista nomes dos bookmarks |
| `get_bookmark(name)` | Documentos em um bookmark |

## Integração com OpenCode

Adicione ao seu `opencode.json`:

```json
{
  "mcpServers": {
    "iped": {
      "command": "python",
      "args": ["-m", "src.main"],
      "cwd": "C:\\iped\\iped-mcp-server"
    }
  }
}
```

## Exemplos de Busca (sintaxe Lucene)

```text
name:*.pdf                    → arquivos PDF
type:jpg                      → arquivos JPG
category:image                → categoria imagem
created:[2023-01-01 TO 2023-12-31]  → intervalo de data
content:"palavra-chave"       → busca em texto completo
*.*                           → todos os itens
```

## Estrutura do Projeto

```
iped-mcp-server/
├── .env                        # Configuração
├── pyproject.toml              # Metadados do projeto
├── requirements.txt            # Dependências Python
└── src/
    ├── __init__.py
    ├── main.py                 # Ponto de entrada FastMCP
    ├── config.py               # Leitura de configuração
    ├── jvm_bridge.py           # Inicialização da JVM via PyJnius
    ├── case_manager.py         # Wrapper para IPEDSource/IPEDMultiSource
    └── tools/
        ├── __init__.py
        ├── sources.py          # list_sources
        ├── search.py           # search, search_by_type, search_by_name
        ├── documents.py        # get_document, get_content, get_text, get_thumb
        └── bookmarks.py        # list_bookmarks, get_bookmark
```
