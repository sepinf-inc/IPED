#!/bin/bash

# Script para executar os testes do módulo iped-tests
# Uso: ./run-tests.sh [profile]

set -e

# Cores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Função para imprimir mensagens coloridas
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Verifica se estamos no diretório correto
if [ ! -f "pom.xml" ]; then
    print_error "Este script deve ser executado no diretório iped-tests"
    exit 1
fi

# Verifica se o Maven está disponível
if ! command -v mvn &> /dev/null; then
    print_error "Maven não encontrado. Instale o Maven primeiro."
    exit 1
fi

# Determina o perfil a ser usado
PROFILE=${1:-"full"}

print_status "Iniciando execução dos testes do IPED..."
print_status "Perfil selecionado: $PROFILE"

# Limpa builds anteriores
print_status "Limpando builds anteriores..."
mvn clean

# Executa os testes com o perfil especificado
print_status "Executando testes com perfil: $PROFILE"

case $PROFILE in
    "quick")
        print_status "Executando testes rápidos (apenas análise básica)..."
        mvn test -Dtest.profile=quick
        ;;
    "full")
        print_status "Executando todos os testes (análise completa + verificações)..."
        mvn test -Dtest.profile=full
        ;;
    "analysis-only")
        print_status "Executando apenas a análise forense..."
        mvn test -Dtest=ImageProcessingTest#test01_ForensicAnalysis
        ;;
    "verification-only")
        print_status "Executando apenas as verificações (requer análise prévia)..."
        mvn test -Dtest=ForensicResultsTest
        ;;
    *)
        print_error "Perfil inválido: $PROFILE"
        echo "Perfis disponíveis:"
        echo "  quick          - Testes rápidos (apenas análise básica)"
        echo "  full           - Todos os testes (padrão)"
        echo "  analysis-only  - Apenas análise forense"
        echo "  verification-only - Apenas verificações (requer análise prévia)"
        exit 1
        ;;
esac

if [ $? -eq 0 ]; then
    print_success "Todos os testes foram executados com sucesso!"
    
    # Mostra informações sobre os resultados
    if [ -d "target/iped-result" ]; then
        print_status "Resultados da análise encontrados em: target/iped-result"
        
        if [ -d "target/iped-result/csv" ]; then
            CSV_COUNT=$(find target/iped-result/csv -name "*.csv" | wc -l)
            print_status "Arquivos CSV gerados: $CSV_COUNT"
        fi
        
        if [ -d "target/iped-result/html" ]; then
            HTML_COUNT=$(find target/iped-result/html -name "*.html" | wc -l)
            print_status "Relatórios HTML gerados: $HTML_COUNT"
        fi
    fi
else
    print_error "Alguns testes falharam. Verifique os logs acima."
    exit 1
fi 