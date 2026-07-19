export type Resume = {
  id: number
  name: string
  targetRole: string | null
  content: string
  createdAt: string
  updatedAt: string
}

export type ResumeInput = {
  name: string
  targetRole: string | null
  content: string
}
