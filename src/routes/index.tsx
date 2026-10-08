import { createFileRoute } from "@tanstack/react-router";
import { MemeStudio } from "@/components/meme-studio";

export const Route = createFileRoute("/")({ component: Home });

function Home() {
  return <MemeStudio />;
}
