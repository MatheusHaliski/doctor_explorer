import Link from "next/link";

export default function NotFound() {
  return (
    <div className="auth">
      <div className="card text-center">
        <h1>Não encontrado</h1>
        <p className="lead">A página não existe ou você não tem acesso a ela.</p>
        <Link className="btn primary" href="/">
          Ir para o início
        </Link>
      </div>
    </div>
  );
}
